package com.jokernan.craftycards.blockentity.base;

import com.jokernan.craftycards.util.Location;
import com.jokernan.craftycards.util.UnitChatMessage;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

import javax.annotation.Nullable;

public abstract class BlockEntityBase extends BaseContainerBlockEntity {
    public BlockEntityBase(BlockEntityType<?> pType, BlockPos pPos, BlockState pBlockState) {
        super(pType, pPos, pBlockState);
    }

    protected UnitChatMessage getUnitName(Player player) {
        return new UnitChatMessage(getLocation().getBlock().getName().getString(), player);
    }

    public Location getLocation() {
        return new Location(level, worldPosition);
    }

    /**
     * 客户端收到方块实体同步包时套用数据。
     *
     * <p><b>这两个方法刻意不写 {@code @Override}</b>：它们是 **NeoForge 加的钩子**，
     * 原版 {@code BlockEntity} 里没有（原版走 {@code loadWithComponents} → {@code loadAdditional}，
     * 效果等价）。写了 {@code @Override} 在 Fabric 侧就编不过（"方法不会覆盖超类型的方法"）；
     * 不写则在 NeoForge 上依旧是重写（签名一致），在 Fabric 上只是两个不会被调用的额外方法——
     * 行为两边都不变，这正是"同一份源码、两种加载器"最省事的处理方式。</p>
     */
    public void onDataPacket(Connection net, ClientboundBlockEntityDataPacket pkt, HolderLookup.Provider lookupProvider) {
        loadAdditional(pkt.getTag(), lookupProvider);
    }

    /** 见 {@link #onDataPacket} 的说明（同样是 NeoForge 的钩子）。 */
    public void handleUpdateTag(CompoundTag tag, HolderLookup.Provider lookupProvider) {
        loadAdditional(tag, lookupProvider);
    }

    @Nullable
    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider lookupProvider) {
        CompoundTag tag = new CompoundTag();
        saveAdditional(tag, lookupProvider);
        return tag;
    }
}
