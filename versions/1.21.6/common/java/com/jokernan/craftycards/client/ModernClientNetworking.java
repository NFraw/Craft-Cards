package com.jokernan.craftycards.client;

import com.jokernan.craftycards.network.payload.PlayerActionPayload;
import net.minecraft.client.Minecraft;

/** Keep Minecraft client types out of the shared server-side network bridge. */
public final class ModernClientNetworking {
    private ModernClientNetworking() {}
    public static void send(PlayerActionPayload payload) {
        var connection = Minecraft.getInstance().getConnection();
        if (connection != null) connection.send(new net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket(payload));
    }
}
