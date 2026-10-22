package com.jokernan.craftycards.network.payload;

import com.jokernan.craftycards.CCReference;
import net.minecraft.nbt.*;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import java.util.UUID;

/** Per-viewer snapshot. Hidden card IDs must never be put in this tag in the first place. */
public record ZJHStatePayload(TableKey table,UUID session,long version,long acknowledgement,CompoundTag data)
    implements CustomPacketPayload {
    public static final Type<ZJHStatePayload> TYPE=new Type<>(CCReference.location("zjh_state"));
    public static final StreamCodec<RegistryFriendlyByteBuf,ZJHStatePayload> STREAM_CODEC=StreamCodec.of(
        (b,p)->{TableKey.STREAM_CODEC.encode(b,p.table);b.writeUUID(p.session);b.writeLong(p.version);
            b.writeLong(p.acknowledgement);b.writeNbt(p.data);},
        b->new ZJHStatePayload(TableKey.STREAM_CODEC.decode(b),b.readUUID(),b.readLong(),b.readLong(),b.readNbt()));
    public ZJHStatePayload {data=data==null?new CompoundTag():data.copy();}
    public int me(){return data.getInt("me");}
    public String phase(){return data.getString("phase");}
    public ListTag players(){return data.getList("players",Tag.TAG_COMPOUND);}
    public boolean closed(){return data.getBoolean("closed");}
    @Override public Type<? extends CustomPacketPayload> type(){return TYPE;}
}
