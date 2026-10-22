package com.jokernan.craftycards.network.payload;

import com.jokernan.craftycards.CCReference;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import java.util.UUID;

/** Dedicated protocol: never changes the ordinal contract of the DDZ actions. */
public record ZJHActionPayload(TableKey table,UUID session,long version,long sequence,Action action,
                               int value,int target,CompoundTag config) implements CustomPacketPayload {
    public enum Action { JOIN, READY, START, REVEAL, CALL, RAISE, COMPARE, FOLD, LEAVE, CONFIG, SAVE, CLOSE, WATCH, LOBBY }
    public static final UUID NONE=new UUID(0,0);
    public static final Type<ZJHActionPayload> TYPE=new Type<>(CCReference.location("zjh_action"));
    public static final StreamCodec<RegistryFriendlyByteBuf,ZJHActionPayload> STREAM_CODEC=StreamCodec.of(
        (b,p)->{TableKey.STREAM_CODEC.encode(b,p.table);b.writeUUID(p.session);b.writeLong(p.version);b.writeLong(p.sequence);
            b.writeEnum(p.action);b.writeInt(p.value);b.writeInt(p.target);b.writeNbt(p.config);},
        b->new ZJHActionPayload(TableKey.STREAM_CODEC.decode(b),b.readUUID(),b.readLong(),b.readLong(),
            b.readEnum(Action.class),b.readInt(),b.readInt(),b.readNbt()));
    public ZJHActionPayload { config=config==null?new CompoundTag():config.copy(); }
    @Override public Type<? extends CustomPacketPayload> type(){return TYPE;}
}
