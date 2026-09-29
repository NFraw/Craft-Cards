package com.jokernan.craftycards.init;

import com.jokernan.craftycards.CCReference;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * 方块实体类型的 **NeoForge 注册**。
 *
 * <p>common 的 {@link InitBlockEntityTypes} 声明把手与共用构造，这里只登记。
 * 必须**排在方块注册之后**：构造时要取牌桌方块实例。</p>
 */
public final class NeoForgeBlockEntityTypes {
    /** 方块实体类型注册表。 */
    private static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITY_TYPES =
        DeferredRegister.create(BuiltInRegistries.BLOCK_ENTITY_TYPE, CCReference.MOD_ID);

    private NeoForgeBlockEntityTypes() {}

    /** 在模组构造函数里调用（要在 {@link NeoForgeItems#register} 之后）。 */
    public static void register(IEventBus bus) {
        InitBlockEntityTypes.DDZ_TABLE.bind(
            BLOCK_ENTITY_TYPES.register("ddz_table", InitBlockEntityTypes::createDdzTable));
        BLOCK_ENTITY_TYPES.register(bus);
    }
}
