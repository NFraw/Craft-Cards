package com.jokernan.craftycards.block;

import com.jokernan.craftycards.block.base.BlockBase;
import com.jokernan.craftycards.blockentity.DDZTableBlockEntity;
import com.jokernan.craftycards.client.ClientDDZData;
import com.jokernan.craftycards.network.payload.TableKey;
import net.minecraft.core.BlockPos;
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
 * 斗地主牌桌方块：单方块（1×1）。
 *
 * <p>交互（客户端把决策发 C2S，服务端权威执行）：
 * <ul>
 *   <li>右键：未入局 = 加入；叫分轮到自己 = 按滚轮所选叫分；出牌轮到自己 = 有选中牌出牌 / 无选中牌过牌</li>
 *   <li>Shift+右键：离开牌局（入局后可自由走动，这是唯一的主动离开方式）</li>
 * </ul>
 * 玩家入局后不再绑定座椅实体，可随处走动、凑近看桌面上的牌。
 *
 * <p>牌桌挂了一个方块实体 {@link DDZTableBlockEntity}，存<b>这一张桌</b>的筹码配置与房主—
 * 服务器管理员只在 `server.json` 里保留「能不能用筹码」这个总闸门，各桌的数据互不影响。
 */
public class BlockDDZTable extends BlockBase implements EntityBlock {
    private static final VoxelShape SHAPE = Block.box(0, 0, 0, 16, 14, 16);

    public BlockDDZTable() {
        super(Block.Properties.of().sound(SoundType.WOOD).strength(2.0F).noOcclusion());
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
