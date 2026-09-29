package com.jokernan.craftycards.network.payload;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

/**
 * 跨维度唯一标识一张斗地主牌桌（维度 + 方块坐标）。
 */
public record TableKey(ResourceKey<Level> dimension, BlockPos pos) {
    public static final StreamCodec<RegistryFriendlyByteBuf, TableKey> STREAM_CODEC = StreamCodec.composite(
        ResourceKey.streamCodec(Registries.DIMENSION), TableKey::dimension,
        BlockPos.STREAM_CODEC, TableKey::pos,
        TableKey::new);

    @Override
    public String toString() {
        return dimension.location() + "@" + pos.toShortString();
    }
}
