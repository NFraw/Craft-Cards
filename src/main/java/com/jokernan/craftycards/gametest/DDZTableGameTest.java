package com.jokernan.craftycards.gametest;

import com.jokernan.craftycards.CCReference;
import com.jokernan.craftycards.block.BlockDDZTable;
import com.jokernan.craftycards.blockentity.DDZTableBlockEntity;
import com.jokernan.craftycards.init.InitItems;
import com.jokernan.craftycards.game.server.ServerGameConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * 斗地主牌桌方块（1×1）的游戏内测试。
 *
 * <p>跑法：{@code ./gradlew.bat runGameTestServer}（无头服务端，跑完自动退出）。
 * 用空结构模板 {@code data/crafty_cards/structure/ddz_table_place.nbt}（7×4×7），
 * 方块放在相对坐标 (3,1,3)。牌桌已是单方块（无多方块结构），此处保留放置/破坏的
 * 基线回归，防止未来再改结构时破坏放置路径。</p>
 */
@GameTestHolder(CCReference.MOD_ID)
@PrefixGameTestTemplate(false)
public class DDZTableGameTest {
    private static final String TEMPLATE = "ddz_table_place";
    /** 牌桌方块在模板内的相对坐标。 */
    private static final BlockPos CORE = new BlockPos(3, 1, 3);

    /**
     * 牌桌自带配置：放下后应挂上方块实体，并把全局默认值种成本桌的初始配置。
     *
     * <p>这是每桌独立配置的地基：数据落在桌子上而不是全局，所以服务器管理员关掉总闸门再打开，
     * 各桌自己的类型／底注／门槛不会被重置这条正是本用例要钉住的。</p>
     */
    @GameTest(template = TEMPLATE)
    public static void tableStoresOwnChipConfig(GameTestHelper helper) {
        helper.setBlock(CORE, InitItems.DDZ_TABLE.get());
        BlockPos pos = helper.absolutePos(CORE);
        if (!(helper.getLevel().getBlockEntity(pos) instanceof DDZTableBlockEntity table)) {
            helper.fail("牌桌应挂上 DDZTableBlockEntity");
            return;
        }
        table.ensureSeeded();
        int stake = Math.max(1, ServerGameConfig.chipStake);
        if (table.stake() != stake) {
            helper.fail("底注应种入全局默认值 " + stake + "，实际 " + table.stake());
            return;
        }
        if (table.entryCount() != Math.max(0, ServerGameConfig.chipEntryCount)) {
            helper.fail("门槛应种入全局默认值，实际 " + table.entryCount());
            return;
        }
        int derived = table.entryCount() > 0 ? table.entryCount() : stake * 6;
        if (!table.chipsEnabled() || table.effectiveEntryCount() != derived) {
            helper.fail("新桌应默认启用筹码，且门槛派生为 底注 x 6");
            return;
        }
        // 核心断言：总闸门关掉再打开，桌子自己的数据一个字都不变
        table.setChipItem("minecraft:diamond");
        table.setStake(3);
        table.setEntryCount(20);
        boolean gate = ServerGameConfig.chipsRequired;
        ServerGameConfig.chipsRequired = false;
        ServerGameConfig.chipsRequired = gate;
        if (table.stake() != 3 || table.entryCount() != 20
                || !"minecraft:diamond".equals(table.chipItem())) {
            helper.fail("总闸门开关不应改动桌子自己的配置");
            return;
        }
        helper.succeed();
    }

    /** 放置成功：相邻格被占用也不影响（单方块无预检）。 */
    @GameTest(template = TEMPLATE)
    public static void placeSucceedsEvenWithNeighbours(GameTestHelper helper) {
        helper.setBlock(CORE.below(), Blocks.STONE);
        helper.setBlock(CORE.offset(1, 0, 1), Blocks.STONE);
        helper.setBlock(CORE, InitItems.DDZ_TABLE.get());

        if (!(helper.getBlockState(CORE).getBlock() instanceof BlockDDZTable)) {
            helper.fail("牌桌放置失败：" + CORE + " 不是 ddz_table");
            return;
        }
        helper.succeed();
    }

    /** 破坏只移除自身，不波及相邻方块（曾经的多方块整桌拆除已移除）。 */
    @GameTest(template = TEMPLATE)
    public static void breakingRemovesOnlyItself(GameTestHelper helper) {
        helper.setBlock(CORE, InitItems.DDZ_TABLE.get());
        BlockPos neighbour = CORE.offset(1, 0, 0);
        helper.setBlock(neighbour, Blocks.STONE);

        helper.destroyBlock(CORE);

        if (helper.getBlockState(CORE).getBlock() instanceof BlockDDZTable) {
            helper.fail("牌桌被拆后仍存在");
            return;
        }
        if (!(helper.getBlockState(neighbour).getBlock() == Blocks.STONE)) {
            helper.fail("破坏牌桌波及了相邻方块");
            return;
        }
        helper.succeed();
    }
}
