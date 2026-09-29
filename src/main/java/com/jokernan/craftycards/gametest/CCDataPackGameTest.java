package com.jokernan.craftycards.gametest;

import com.jokernan.craftycards.CCReference;
import com.jokernan.craftycards.init.InitItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;


/**
 * 数据包资源（配方 / 掉落表）与方块形状的游戏内测试。
 *
 * <p>存在的理由：1.21 把数据包目录名改成了单数（{@code recipe/}、{@code loot_table/}），
 * 本项目原来用的是旧复数名 {@code recipes/}、{@code loot_tables/}——游戏压根不会去扫这两个
 * 目录，于是 7 个配方与 3 个掉落表全部静默失效（合成不出来、破坏方块也不掉落），
 * 而且没有任何报错。这类"资源静默不生效"只能靠运行时查询来固定。</p>
 */
@GameTestHolder(CCReference.MOD_ID)
@PrefixGameTestTemplate(false)
public class CCDataPackGameTest {
    private static final String TEMPLATE = "ddz_table_place";
    private static final BlockPos CORE = new BlockPos(3, 1, 3);

    /** 配方确实被加载了（目录名必须是 1.21 的单数形式）。 */
    @GameTest(template = TEMPLATE)
    public static void recipesAreLoaded(GameTestHelper helper) {
        var manager = helper.getLevel().getServer().getRecipeManager();
        String[] ids = {
            "crafty_cards:blocks/ddz_table",
        };
        for (String id : ids) {
            if (manager.byKey(ResourceLocation.parse(id)).isEmpty()) {
                helper.fail("配方未加载：" + id + "（检查 data/crafty_cards/recipe/ 目录名是否为单数）");
                return;
            }
        }
        helper.succeed();
    }

    /** 每个方块都要有掉落表，否则破坏后什么都不掉。 */
    @GameTest(template = TEMPLATE)
    public static void blocksHaveLootTables(GameTestHelper helper) {
        String[] tables = {
            "crafty_cards:blocks/ddz_table",
            "crafty_cards:blocks/casino_carpet_space",
        };
        var lootTables = helper.getLevel().getServer().reloadableRegistries().lookup()
            .lookupOrThrow(Registries.LOOT_TABLE);
        for (String id : tables) {
            var key = ResourceKey.create(Registries.LOOT_TABLE, ResourceLocation.parse(id));
            if (lootTables.get(key).isEmpty()) {
                helper.fail("掉落表未加载：" + id + "（检查 data/crafty_cards/loot_table/ 目录名是否为单数）");
                return;
            }
        }
        helper.succeed();
    }

    /** 赌场地毯必须是薄片：碰撞箱高度约 1/16 格，而不是整格。 */
    @GameTest(template = TEMPLATE)
    public static void casinoCarpetIsThin(GameTestHelper helper) {
        helper.setBlock(CORE.below(), Blocks.STONE);
        helper.setBlock(CORE, InitItems.CASINO_CARPET_SPACE.get());
        var state = helper.getBlockState(CORE);
        double height = state.getCollisionShape(helper.getLevel(), helper.absolutePos(CORE))
            .max(Direction.Axis.Y);
        if (height > 0.1) {
            helper.fail("赌场地毯的碰撞箱高度是 " + height + " 格，应为 1/16（约 0.0625）——"
                + "模型是薄地毯却按整格碰撞，走上去会被顶起一整格");
            return;
        }
        helper.succeed();
    }

}
