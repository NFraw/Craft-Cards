package com.jokernan.craftycards.platform;

import com.jokernan.craftycards.network.payload.GameStatePayload;
import com.jokernan.craftycards.network.payload.PlayerActionPayload;
import net.minecraft.server.level.ServerPlayer;

/**
 * 网络发送接缝：common 层只会"说"这两句话，具体怎么发由各加载器实现。
 *
 * <p>原先这两处直接调 {@code PacketDistributor}（NeoForge 专有）：服务端逐份发快照
 * （{@code sendToPlayer}）、客户端发玩家动作（{@code sendToServer}）。Fabric 侧对应
 * {@code ServerPlayNetworking} / {@code ClientPlayNetworking} —— 收发管道不同，
 * 但**用哪个 payload、payload 里有什么**完全相同（payload 记录本身就在 common 里）。</p>
 *
 * <p>方向性：{@link #sendToServer} 只在客户端被调用，{@link #sendToPlayer} 只在服务端被调用。
 * 一个实现类同时实现两者即可（另一边永远不会被触发）。</p>
 *
 * <p>未安装实现时是"什么都不发"的空实现：单测与没有加载器的环境不会 NPE，
 * 只是没有任何包发出去（这比抛异常更适合"音频/网络都是锦上添花"的定位）。</p>
 */
public final class Network {
    /** 各加载器实现这两个方法（NeoForge: {@code PacketDistributor}；Fabric: {@code *PlayNetworking}）。 */
    public interface Bridge {
        /** 客户端 → 服务端：发一个玩家动作（加入/叫分/出牌/过牌/离开/配置…）。 */
        void sendToServer(PlayerActionPayload payload);

        /** 服务端 → 某个玩家：发一份完整快照（按接收者单独构建，不共用广播）。 */
        void sendToPlayer(ServerPlayer player, GameStatePayload payload);
    }

    private static volatile Bridge bridge = new Bridge() {
        @Override
        public void sendToServer(PlayerActionPayload payload) {
            // 未安装实现：什么都不发（见类注释）
        }

        @Override
        public void sendToPlayer(ServerPlayer player, GameStatePayload payload) {
            // 同上
        }
    };

    private Network() {}

    /** 由各加载器入口尽早安装实现。 */
    public static void install(Bridge implementation) {
        if (implementation != null) {
            bridge = implementation;
        }
    }

    /** 客户端发动作。 */
    public static void sendToServer(PlayerActionPayload payload) {
        bridge.sendToServer(payload);
    }

    /** 服务端给某个玩家发快照。 */
    public static void sendToPlayer(ServerPlayer player, GameStatePayload payload) {
        bridge.sendToPlayer(player, payload);
    }
}
