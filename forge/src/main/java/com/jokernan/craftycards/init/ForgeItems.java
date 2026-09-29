package com.jokernan.craftycards.init;

import com.jokernan.craftycards.CCReference;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;

/**
 * 方块 / 物品 / 创造模式标签页的 **Forge 注册**。
 *
 * <p>common 的 {@link InitItems} 声明把手与共用的构造逻辑，这里只做登记。
 * 顺序有讲究：**先登记方块，再登记物品**——方块物品的构造要取方块实例，
 * 而 {@code DeferredHolder} 在注册完成前取值会抛（Forge 的注册在
 * {@code RegisterEvent} 里按注册表逐个完成，方块注册表先跑完，物品构造里取值才是安全的）。</p>
 */
public final class ForgeItems {
    /** 方块注册表。 */
    private static final DeferredRegister<net.minecraft.world.level.block.Block> BLOCKS = DeferredRegister.create(Registries.BLOCK, CCReference.MOD_ID);
    /** 物品注册表。 */
    private static final DeferredRegister<net.minecraft.world.item.Item> ITEMS = DeferredRegister.create(Registries.ITEM, CCReference.MOD_ID);
    /** 创造模式标签页注册表。 */
    private static final DeferredRegister<CreativeModeTab> TABS =
        DeferredRegister.create(Registries.CREATIVE_MODE_TAB, CCReference.MOD_ID);

    private ForgeItems() {}

    /** 在模组构造函数里调用。 */
    public static void register(IEventBus bus) {
        // 方块
        InitItems.CASINO_CARPET_SPACE.bind(
            BLOCKS.register("casino_carpet_space", InitItems::createCasinoCarpet));
        InitItems.DDZ_TABLE.bind(BLOCKS.register("ddz_table", InitItems::createDdzTable));

        // 物品（方块物品延迟取方块实例，见类注释）
        InitItems.CASINO_CARPET_SPACE_ITEM.bind(ITEMS.register("casino_carpet_space",
            () -> InitItems.createBlockItem(InitItems.CASINO_CARPET_SPACE.get())));
        InitItems.CARD_COVERED.bind(ITEMS.register("card_covered", InitItems::createCardCovered));
        InitItems.CARD.bind(ITEMS.register("card", InitItems::createCard));
        InitItems.DDZ_CARD.bind(ITEMS.register("ddz_card", InitItems::createDdzCard));
        InitItems.DDZ_TABLE_ITEM.bind(ITEMS.register("ddz_table",
            () -> InitItems.createBlockItem(InitItems.DDZ_TABLE.get())));

        // 创造模式标签页（展示哪些物品由 common 决定，两个加载器一致）
        TABS.register(CCReference.MOD_ID, () -> CreativeModeTab.builder()
            .icon(() -> new ItemStack(InitItems.CARD.get()))
            .displayItems((params, output) -> InitItems.fillCreativeTab(output))
            .title(Component.translatable(InitItems.creativeTabTranslationKey()))
            .build());

        BLOCKS.register(bus);
        ITEMS.register(bus);
        TABS.register(bus);
    }
}

