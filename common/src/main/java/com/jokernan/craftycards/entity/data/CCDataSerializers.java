package com.jokernan.craftycards.entity.data;

import com.jokernan.craftycards.platform.RegHolder;
import com.jokernan.craftycards.util.ArrayHelper;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.syncher.EntityDataSerializer;

/**
 * 自定义实体数据序列化器（当前只有 {@link #STACK}：卡牌的堆叠内容）。
 *
 * <p><b>登记方式在两个加载器上差别最大的一处</b>：NeoForge 用自家注册表
 * （{@code NeoForgeRegistries.ENTITY_DATA_SERIALIZERS}，见 {@code init/NeoForgeDataSerializers}），
 * Fabric 调用原版公开的 {@code EntityDataSerializers.registerSerializer}，不需要 accessor。
 * common 这边只持有把手，不去管谁登记、怎么登记。</p>
 *
 * <p>影响面：{@code STACK} 没登记时，只有<b>实体卡牌</b>（{@code EntityCard} / {@code EntityCardDeck}
 * 及其基类 {@code EntityStacked}）会被影响——斗地主本身是虚拟发牌，牌局与 HUD 不碰它。</p>
 */
public final class CCDataSerializers {
    /** 卡牌堆叠内容（{@code Byte[]} ↔ 字节数组）的把手。 */
    public static final RegHolder<EntityDataSerializer<Byte[]>> STACK = RegHolder.create("stack");

    private CCDataSerializers() {}

    /** 构造序列化器（两个加载器共用；登记动作各自做）。 */
    public static EntityDataSerializer<Byte[]> createStack() {
        return EntityDataSerializer.forValueType(
            StreamCodec.of(
                (RegistryFriendlyByteBuf buf, Byte[] bytes) -> buf.writeByteArray(ArrayHelper.toPrimitive(bytes)),
                (RegistryFriendlyByteBuf buf) -> ArrayHelper.toObject(buf.readByteArray())
            )
        );
    }
}
