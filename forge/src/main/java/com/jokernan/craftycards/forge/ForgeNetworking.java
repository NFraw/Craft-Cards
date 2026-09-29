package com.jokernan.craftycards.forge;

import com.jokernan.craftycards.CCReference;
import com.jokernan.craftycards.game.server.DDZTableManager;
import com.jokernan.craftycards.network.payload.*;
import com.jokernan.craftycards.platform.Network;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.*;

/** Forge transport; both directions reuse the common payload codecs. */
public final class ForgeNetworking implements Network.Bridge {
    private static final SimpleChannel CHANNEL = ChannelBuilder.named(CCReference.location("main"))
        .networkProtocolVersion(1).simpleChannel();

    public static void register() {
        CHANNEL.messageBuilder(PlayerActionPayload.class, 0, NetworkDirection.PLAY_TO_SERVER)
            .codec(PlayerActionPayload.STREAM_CODEC)
            .consumerMainThread((payload, context) -> {
                if (context.getSender() != null)
                    DDZTableManager.getInstance().handle(context.getSender(), payload);
            }).add();
        CHANNEL.messageBuilder(GameStatePayload.class, 1, NetworkDirection.PLAY_TO_CLIENT)
            .codec(GameStatePayload.STREAM_CODEC)
            .consumerMainThread((payload, context) -> ForgeClient.receive(payload)).add();
        CHANNEL.build();
        Network.install(new ForgeNetworking());
    }

    public void sendToServer(PlayerActionPayload payload) {
        CHANNEL.send(payload, PacketDistributor.SERVER.noArg());
    }
    public void sendToPlayer(ServerPlayer player, GameStatePayload payload) {
        CHANNEL.send(payload, PacketDistributor.PLAYER.with(player));
    }
}
