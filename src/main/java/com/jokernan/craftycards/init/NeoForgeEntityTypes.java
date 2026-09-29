package com.jokernan.craftycards.init;

import com.jokernan.craftycards.CCReference;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.EntityType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * 实体类型的 **NeoForge 注册**。
 *
 * <p>common 的 {@link InitEntityTypes} 声明把手与**共用的构造逻辑**（尺寸、分类），
 * 这里只做登记：Fabric 侧是同一份构造 + {@code Registry.register}。</p>
 */
public final class NeoForgeEntityTypes {
    /** 实体类型注册表。 */
    private static final DeferredRegister<EntityType<?>> ENTITY_TYPES =
        DeferredRegister.create(BuiltInRegistries.ENTITY_TYPE, CCReference.MOD_ID);

    private NeoForgeEntityTypes() {}

    /** 在模组构造函数里调用。 */
    public static void register(IEventBus bus) {
        InitEntityTypes.CARD.bind(ENTITY_TYPES.register("card", InitEntityTypes::createCard));
        InitEntityTypes.CARD_DECK.bind(ENTITY_TYPES.register("card_deck", InitEntityTypes::createCardDeck));
        ENTITY_TYPES.register(bus);
    }
}
