package com.jokernan.craftycards.network;

import com.jokernan.craftycards.network.handler.ClientPayloadHandler;
import com.jokernan.craftycards.network.handler.ServerPayloadHandler;
import com.jokernan.craftycards.network.payload.GameStatePayload;
import com.jokernan.craftycards.network.payload.PlayerActionPayload;
import com.jokernan.craftycards.platform.Network;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/**
 * 斗地主网络包注册中心。
 * <p>
 * 在 {@link RegisterPayloadHandlersEvent} 中注册 C2S（客户端→服务端）和 S2C（服务端→客户端）载荷类型。
 * 注册版本号为 {@code "1"}，用于网络协议版本校验。
 * </p>
 *
 * <h3>网络包类型</h3>
 * <ul>
 *   <li>{@link PlayerActionPayload} — C2S：玩家操作（加入/叫分/出牌/过牌/离开）</li>
 *   <li>{@link GameStatePayload} — S2C：全量游戏状态快照（每次操作后广播）</li>
 * </ul>
 *
 * @see ClientPayloadHandler 客户端载荷处理器
 * @see ServerPayloadHandler 服务端载荷处理器
 */
public class DDZNetworking {
    /**
     * 注册所有网络载荷类型。
     * <p>在模组构造函数中通过 {@code modEventBus.addListener(DDZNetworking::registerPayloads)} 注册。</p>
     *
     * @param event 载荷注册事件
     */
    public static void registerPayloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1");
        // C2S：客户端发送玩家操作到服务端
        registrar.playToServer(PlayerActionPayload.TYPE, PlayerActionPayload.STREAM_CODEC, new ServerPayloadHandler());
        // S2C：服务端广播游戏状态快照到客户端
        registrar.playToClient(GameStatePayload.TYPE, GameStatePayload.STREAM_CODEC, new ClientPayloadHandler());
    }

    /**
     * 平台接缝的实现：common 层的 {@link Network.Bridge} 在 NeoForge 上就是 {@code PacketDistributor}。
     *
     * <p>两个方向各一行。多加载器改造要替换掉的"最后一百米"正是这种地方：
     * **语义**（发什么包、发给谁）留在 common，**管道**（谁来发）按加载器换
     * （Fabric 侧是 {@code ServerPlayNetworking} / {@code ClientPlayNetworking}）。</p>
     */
    public static final class PacketDistributorBridge implements Network.Bridge {
        @Override
        public void sendToServer(PlayerActionPayload payload) {
            PacketDistributor.sendToServer(payload);
        }

        @Override
        public void sendToPlayer(ServerPlayer player, GameStatePayload payload) {
            PacketDistributor.sendToPlayer(player, payload);
        }
    }
}
