package com.jokernan.craftycards.init;

import com.jokernan.craftycards.CCReference;
import com.jokernan.craftycards.entity.data.CCDataSerializers;
import net.minecraft.network.syncher.EntityDataSerializer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/**
 * 实体数据序列化器的 **NeoForge 注册**（进 NeoForge 自家的
 * {@code ENTITY_DATA_SERIALIZERS} 注册表）。
 *
 * <p>common 的 {@link CCDataSerializers} 只持有把手与共用构造。Fabric 侧没有这个注册表
 * （原版把序列化器放在静态列表里、无公开登记入口），那边要另想办法——见该类的说明。</p>
 */
public final class NeoForgeDataSerializers {
    /** 序列化器注册表。 */
    private static final DeferredRegister<EntityDataSerializer<?>> SERIALIZERS =
        DeferredRegister.create(NeoForgeRegistries.ENTITY_DATA_SERIALIZERS, CCReference.MOD_ID);

    private NeoForgeDataSerializers() {}

    /** 在模组构造函数里调用。 */
    public static void register(IEventBus bus) {
        CCDataSerializers.STACK.bind(SERIALIZERS.register("stack", CCDataSerializers::createStack));
        SERIALIZERS.register(bus);
    }
}
