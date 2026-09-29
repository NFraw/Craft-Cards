package com.jokernan.craftycards.init;

import com.jokernan.craftycards.CCReference;
import com.jokernan.craftycards.block.BlockDDZTable;
import com.jokernan.craftycards.block.base.BlockItemBase;
import com.jokernan.craftycards.item.ItemCard;
import com.jokernan.craftycards.item.ItemCardCovered;
import com.jokernan.craftycards.item.ItemCardDeck;
import com.jokernan.craftycards.item.base.ItemBase;
import com.jokernan.craftycards.platform.RegHolder;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CarpetBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;

import java.util.List;

/**
 * 方块与物品注册中心：**声明**有哪些内容、每个怎么构造。
 *
 * <p>登记动作在加载器侧（NeoForge 的 {@code init/NeoForgeItems}、Fabric 的对应类）：
 * 它们按下面的把手逐个注册，再把注册出来的对象 bind 回来（见 {@link RegHolder}）。
 * 两个加载器都调用这里同一份 {@code create*} 工厂，所以内容不会各写各的。</p>
 *
 * <h3>内容清单</h3>
 * <ul>
 *   <li>{@code casino_carpet_space} 赌场地毯（方块 + 方块物品）</li>
 *   <li>{@code card} / {@code card_covered} 正面牌 / 牌背牌</li>
 *   <li>{@code ddz_table} 斗地主牌桌（方块 + 方块物品）</li>
 *   <li>{@code ddz_card} 斗地主扑克（入局时发放，不出现在创造标签页）</li>
 * </ul>
 *
 * <p>下线的内容（扑克桌 / 酒吧凳 / 筹码 / 骰子 / 牌堆物品）连同注册一起删掉了——
 * 留着注册却没有资源或渲染器，正是"旧存档一进就崩"的成因。</p>
 */
public final class InitItems {
    // === 方块 ===

    /**
     * 赌场地毯方块 —— 用原版 {@link CarpetBlock} 而不是裸 {@code Block}：
     * 裸 Block 的碰撞箱是整个 1×1×1 立方体，而模型（parent: block/carpet）只有 1/16 高，
     * 于是看着是薄地毯、走上去却像撞一堵齐腰的墙，站在上面还会被顶起一整格。
     * CarpetBlock 自带 1/16 高的形状/碰撞、需要下方有支撑、且不遮挡光照，与羊毛地毯一致。
     */
    public static final RegHolder<Block> CASINO_CARPET_SPACE = RegHolder.create("casino_carpet_space");

    /**
     * 斗地主牌桌方块 — 放置后玩家右键加入牌局，3 人坐满自动开局。
     * <p>客户端右键行为委托给 {@code client/ClientDDZData#onTableRightClicked}，
     * 服务端入局由 C2S 包处理器完成。</p>
     */
    public static final RegHolder<Block> DDZ_TABLE = RegHolder.create("ddz_table");

    // === 物品 ===

    /** 赌场地毯方块对应的物品。 */
    public static final RegHolder<BlockItem> CASINO_CARPET_SPACE_ITEM = RegHolder.create("casino_carpet_space");

    /**
     * 覆盖牌（牌背朝上）— 单张卡牌物品，右键可翻面。
     * <p>使用 damage 值（0-53）存储牌面 ID，NBT 中存储 SkinID、UUID、Covered 等属性。</p>
     */
    public static final RegHolder<Item> CARD_COVERED = RegHolder.create("card_covered");

    /**
     * 正面牌（牌面朝上）— 继承自 {@link ItemCardCovered}，右键可翻回牌背。
     * <p>鼠标悬停时显示牌面名称（如"A ♠"）。</p>
     */
    public static final RegHolder<Item> CARD = RegHolder.create("card");

    /**
     * 斗地主扑克（占位物品）：入局时发给玩家，握在手上才会展开自己的手牌。
     * <p>设计目的是让玩家能随时切回武器/工具而不用退出牌局——切走只是收起牌面，牌局照旧；
     * 牌局结束/解散时由服务端回收。图案用的是小王。**不进创造标签页**（放出来只会让玩家困惑）。</p>
     */
    public static final RegHolder<Item> DDZ_CARD = RegHolder.create("ddz_card");

    /** 斗地主牌桌方块对应的物品。 */
    public static final RegHolder<BlockItem> DDZ_TABLE_ITEM = RegHolder.create("ddz_table");

    private InitItems() {}

    // === 构造逻辑（两个加载器共用）===

    /** 构造赌场地毯方块（见字段上的说明：必须是 CarpetBlock）。 */
    public static Block createCasinoCarpet() {
        return new CarpetBlock(BlockBehaviour.Properties.of()
            .sound(SoundType.WOOL).strength(0.1F).ignitedByLava());
    }

    /** 构造斗地主牌桌方块。 */
    public static Block createDdzTable() {
        return new BlockDDZTable();
    }

    /** 构造牌背牌物品。 */
    public static Item createCardCovered() {
        return new ItemCardCovered();
    }

    /** 构造正面牌物品。 */
    public static Item createCard() {
        return new ItemCard();
    }

    /** 构造斗地主扑克物品（不可堆叠）。 */
    public static Item createDdzCard() {
        return new ItemBase(new Item.Properties().stacksTo(1));
    }

    /**
     * 构造可放置方块的物品。
     *
     * <p>注意这是**取用时**才调用 {@code block.get()}：NeoForge 的 {@code DeferredHolder}
     * 在注册完成前取值会抛异常，所以加载器侧必须把方块物品的构造包在延迟 lambda 里，
     * 且让方块先于物品注册（同一次注册事件里方块先完成）。</p>
     */
    public static BlockItem createBlockItem(Block block) {
        return new BlockItemBase(block);
    }

    // === 创造模式标签页 ===

    /**
     * 进创造标签页的物品（顺序稳定）。
     *
     * <p>游戏内部物品不进：牌背牌由右键翻面产生，斗地主扑克由入局时发放。</p>
     */
    public static List<RegHolder<? extends Item>> creativeTabItems() {
        return List.of(CASINO_CARPET_SPACE_ITEM, CARD, DDZ_TABLE_ITEM);
    }

    /**
     * 把本模组的物品填进创造模式标签页——**展示逻辑两个加载器共用这一份**。
     *
     * <p>牌堆物品已下线，所以那个"展示所有牌背皮肤变体"的分支当前不会走到；
     * 保留它是为了将来重新上线牌堆时不用再想一遍。</p>
     */
    public static void fillCreativeTab(CreativeModeTab.Output output) {
        for (RegHolder<? extends Item> holder : creativeTabItems()) {
            Item item = holder.get();
            if (item instanceof ItemCardDeck deck) {
                deck.fillItemGroup(output);
            } else {
                output.accept(new ItemStack(item));
            }
        }
    }

    /** 创造标签页的标题翻译键。 */
    public static String creativeTabTranslationKey() {
        return "itemGroup." + CCReference.MOD_ID + ".tab";
    }
}
