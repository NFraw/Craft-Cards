package com.jokernan.craftycards.fabric;

import com.jokernan.craftycards.CCReference;
import com.jokernan.craftycards.game.server.DDZTableManager;
import com.jokernan.craftycards.network.payload.GameStatePayload;
import com.jokernan.craftycards.network.payload.PlayerActionPayload;
import com.jokernan.craftycards.platform.Network;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Fabric 侧的**网络**：包类型注册、C2S 接收、以及 common 的 {@link Network.Bridge} 实现。
 *
 * <p>语义与 NeoForge 侧逐字对应（那边的 {@code DDZNetworking} + 两个 payload handler）：
 * 客户端发 {@link PlayerActionPayload}（加入/叫分/出牌/过牌/离开/配置…），
 * 服务端按接收者逐份发 {@link GameStatePayload} 全量快照。
 * 不同的只是管道名（NeoForge: {@code PacketDistributor}；Fabric: {@code *PlayNetworking}）。</p>
 *
 * <p>线程模型与 NeoForge 一致：接收器跑在网络线程，所以**必须**把处理丢回主线程
 * （服务端用 {@code player.server.execute}，客户端在
 * {@link CraftyCardsFabricClient} 里用 {@code client.execute}）。</p>
 */
public final class FabricNetworking {
    private static final Logger LOG = LoggerFactory.getLogger(CCReference.MOD_ID);

    private FabricNetworking() {}

    /** 客户端与服务端都要跑的部分（在模组入口调用）。 */
    public static void registerCommon() {
        PayloadTypeRegistry.playC2S().register(PlayerActionPayload.TYPE, PlayerActionPayload.STREAM_CODEC);
        PayloadTypeRegistry.playS2C().register(GameStatePayload.TYPE, GameStatePayload.STREAM_CODEC);

        // C2S：玩家操作 → 牌桌管理器（与 NeoForge 的 ServerPayloadHandler 同一句调用）
        ServerPlayNetworking.registerGlobalReceiver(PlayerActionPayload.TYPE, (payload, context) ->
            context.player().server.execute(
                () -> DDZTableManager.getInstance().handle(context.player(), payload)));

        Network.install(new PlayNetworkingBridge());
        LOG.info("Crafty Cards (Fabric) 网络已注册（C2S 玩家操作 / S2C 牌局快照）");
    }

    /**
     * common 的发送接缝在这个加载器上的实现：两个方向各一行。
     *
     * <p>{@code sendToServer} 只在客户端被调用、{@code sendToPlayer} 只在服务端被调用，
     * 所以一个类同时实现两者即可（另一边的 API 永远不会被解析到）。</p>
     */
    private static final class PlayNetworkingBridge implements Network.Bridge {
        @Override
        public void sendToServer(PlayerActionPayload payload) {
            // 注意包名里的 client：Fabric 把客户端网络 API 放在 api.client.networking.v1 下
            net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(payload);
        }

        @Override
        public void sendToPlayer(ServerPlayer player, GameStatePayload payload) {
            // 对方没装这个通道时直接发会抛异常；牌局快照是"有则更好"的东西，
            // 所以跟 NeoForge 侧一样宁可少发一份，也不要把服务端 tick 带崩
            if (ServerPlayNetworking.canSend(player, GameStatePayload.TYPE)) {
                ServerPlayNetworking.send(player, payload);
            }
        }
    }
}
