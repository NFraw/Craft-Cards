package com.jokernan.craftycards.init;

import com.jokernan.craftycards.CCReference;
import com.jokernan.craftycards.entity.EntityCard;
import com.jokernan.craftycards.entity.EntityCardDeck;
import com.jokernan.craftycards.platform.RegHolder;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;

/**
 * 实体类型注册中心（卡牌 / 整副牌堆）。
 *
 * <p>common 只声明"有哪些实体类型、怎么构造"，**登记**由各加载器做
 * （NeoForge 的 {@code init/NeoForgeEntityTypes}、Fabric 的对应类）：两边调用**同一份**
 * {@link #createCard()} / {@link #createCardDeck()}，尺寸与分类不会各写各的。</p>
 *
 * <p>所有实体均归类为 {@link MobCategory#MISC}（无 AI、无刷怪蛋）。</p>
 *
 * <h3>实体尺寸说明</h3>
 * <ul>
 *   <li>卡牌 / 牌堆：0.5×0.5 格</li>
 * </ul>
 *
 * <p>已下线的实体类型（筹码 / 骰子 / 座椅）连同注册一起删掉了：留着类型却没有渲染器会让
 * 旧存档一进就崩（见 AGENTS 的"下线功能时要成对处理"），所以这儿不保留"文物"。</p>
 *
 * @see com.jokernan.craftycards.render 实体渲染器
 */
public final class InitEntityTypes {
    /**
     * 卡牌实体 — 世界中的一摞卡牌（可包含多张）。
     * <p>右键从顶部取牌，Shift 右键添加牌，攻击翻面。</p>
     */
    public static final RegHolder<EntityType<EntityCard>> CARD = RegHolder.create("card");

    /**
     * 牌堆实体 — 完整的 54 张牌堆，右键从顶部发牌。
     * <p>攻击（非 Shift）洗牌，Shift 攻击收回为物品。</p>
     * <p>牌堆<b>物品</b>已下线（玩家无法再放置牌堆），但实体类型必须保留：
     * 卡牌物品 {@code ItemCardCovered} / {@code EntityCard} 的翻面逻辑会查找附近的牌堆实体。</p>
     */
    public static final RegHolder<EntityType<EntityCardDeck>> CARD_DECK = RegHolder.create("card_deck");

    private InitEntityTypes() {}

    /** 构造卡牌实体类型（两个加载器共用这一份）。 */
    public static EntityType<EntityCard> createCard() {
        return EntityType.Builder.<EntityCard>of(EntityCard::new, MobCategory.MISC)
            .sized(0.5F, 0.5F)
            .build(CCReference.location("card").toString());
    }

    /** 构造牌堆实体类型（两个加载器共用这一份）。 */
    public static EntityType<EntityCardDeck> createCardDeck() {
        return EntityType.Builder.<EntityCardDeck>of(EntityCardDeck::new, MobCategory.MISC)
            .sized(0.5F, 0.5F)
            .build(CCReference.location("card_deck").toString());
    }
}
