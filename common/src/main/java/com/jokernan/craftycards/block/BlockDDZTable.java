package com.jokernan.craftycards.block;

import com.jokernan.craftycards.block.base.BlockBase;
import com.jokernan.craftycards.blockentity.DDZTableBlockEntity;
import com.jokernan.craftycards.client.ClientDDZData;
import com.jokernan.craftycards.network.payload.TableKey;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import com.jokernan.craftycards.game.server.DDZTableManager;
import com.jokernan.craftycards.game.DDZTableLayout;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import javax.annotation.Nullable;

/**
 * 斗地主牌桌方块：六面封闭的完整单方块（1×1×1）。
 *
 * <p>交互（客户端把决策发 C2S，服务端权威执行）：
 * <ul>
 *   <li>右键：未入局 = 加入；叫分轮到自己 = 按滚轮所选叫分；出牌轮到自己 = 有选中牌出牌 / 无选中牌过牌</li>
 *   <li>Shift+右键：离开牌局（入局后可自由走动，这是唯一的主动离开方式）</li>
 * </ul>
 * 玩家入局后不再绑定座椅实体，可随处走动、凑近看桌面上的牌。
 *
 * <p>牌桌挂了一个方块实体 {@link DDZTableBlockEntity}，存<b>这一张公共桌</b>的筹码配置与临时编辑锁—
 * 服务器管理员只在 `server.json` 里保留「能不能用筹码」这个总闸门，各桌的数据互不影响。
 */
public class BlockDDZTable extends BlockBase implements EntityBlock {
    public static final BooleanProperty NORTH = BlockStateProperties.NORTH;
    public static final BooleanProperty EAST = BlockStateProperties.EAST;
    public static final BooleanProperty SOUTH = BlockStateProperties.SOUTH;
    public static final BooleanProperty WEST = BlockStateProperties.WEST;
    private static final VoxelShape SHAPE = Block.box(0, 0, 0, 16, 16, 16);

    public BlockDDZTable() {
        super(Block.Properties.of().sound(SoundType.WOOD).strength(2.0F));
        registerDefaultState(stateDefinition.any().setValue(NORTH, false).setValue(EAST, false)
            .setValue(SOUTH, false).setValue(WEST, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(NORTH, EAST, SOUTH, WEST);
    }

    private static BooleanProperty connection(Direction direction) {
        return switch (direction) {
            case NORTH -> NORTH; case EAST -> EAST; case SOUTH -> SOUTH; case WEST -> WEST;
            default -> throw new IllegalArgumentException("Expected horizontal direction");
        };
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockPos candidate = context.getClickedPos();
        if (!DDZTableLayout.canAdd(candidate.getX(), candidate.getZ(), (x, z) -> {
            BlockPos pos = new BlockPos(x, candidate.getY(), z);
            return context.getLevel().hasChunkAt(pos) && context.getLevel().getBlockState(pos).is(this);
        })) {
            if (context.getPlayer() != null && !context.getLevel().isClientSide)
                context.getPlayer().displayClientMessage(Component.literal("一张牌桌最多只能由 9 个方块组成"), true);
            return null;
        }
        BlockState state = defaultBlockState();
        for (Direction direction : Direction.Plane.HORIZONTAL)
            state = state.setValue(connection(direction), context.getLevel()
                .getBlockState(context.getClickedPos().relative(direction)).is(this));
        return state;
    }

    @Override
    public BlockState updateShape(BlockState state, Direction direction, BlockState neighbour,
                                  LevelAccessor level, BlockPos pos, BlockPos neighbourPos) {
        return direction.getAxis().isHorizontal()
            ? state.setValue(connection(direction), neighbour.is(this)) : state;
    }

    /** Old saved tables have no connection properties; refresh on chunk load as well as placement. */
    @Override
    public void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        refreshConnections(level, pos);
        for (Direction direction : Direction.Plane.HORIZONTAL)
            refreshConnections(level, pos.relative(direction));
    }

    private void refreshConnections(ServerLevel level, BlockPos pos) {
        if (!level.hasChunkAt(pos)) return;
        BlockState old = level.getBlockState(pos);
        if (!old.is(this)) return;
        BlockState updated = old;
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            BlockPos adjacent = pos.relative(direction);
            updated = updated.setValue(connection(direction), level.hasChunkAt(adjacent)
                && level.getBlockState(adjacent).is(this));
        }
        if (updated != old) level.setBlock(pos, updated, Block.UPDATE_ALL);
    }

    @Override
    public void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean moving) {
        super.onPlace(state, level, pos, oldState, moving);
        if (!level.isClientSide && !oldState.is(this))
            DDZTableManager.getInstance().handleStructureChanged(level, pos);
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moving) {
        if (!level.isClientSide && !newState.is(this))
            DDZTableManager.getInstance().handleStructureChanged(level, pos);
        super.onRemove(state, level, pos, newState, moving);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level.isClientSide) {
            // 客户端专属逻辑收在 ClientDDZData 里，方块类字节码只保留纯方法调用，
            // 避免服务端类加载时解析到 Screen/Minecraft 等 @OnlyIn(CLIENT) 类而崩溃。
            ClientDDZData.getInstance().onTableRightClicked(new TableKey(level.dimension(), pos), player.isShiftKeyDown());
        }
        return InteractionResult.SUCCESS;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new DDZTableBlockEntity(pos, state);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }
}
