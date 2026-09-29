package com.jokernan.craftycards.network.handler;

import com.jokernan.craftycards.game.server.DDZTableManager;
import com.jokernan.craftycards.network.payload.PlayerActionPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.handling.IPayloadHandler;

/**
 * 服务端载荷处理器 — 接收客户端发送的 {@link PlayerActionPayload} 操作。
 * <p>
 * 在服务端网络线程收到 C2S 包后调用，验证发送者为 {@link ServerPlayer} 后，
 * 通过 {@code enqueueWork} 将处理逻辑调度到服务端主线程执行。
 * </p>
 *
 * @see DDZTableManager#handle 分发玩家操作到对应牌局会话
 * @see DDZNetworking#registerPayloads 注册入口
 */
public class ServerPayloadHandler implements IPayloadHandler<PlayerActionPayload> {
    /**
     * 处理客户端发送的玩家操作。
     * <p>
     * 将操作传递给全局 {@link DDZTableManager} 单例，
     * 由管理器根据 {@link PlayerActionPayload#table()} 分发到对应的 {@link com.jokernan.craftycards.game.server.DDZSession}。
     * </p>
     *
     * @param payload 客户端发送的玩家操作（加入/叫分/出牌/过牌/离开）
     * @param context 网络上下文（用于获取发送者和调度到主线程）
     */
    @Override
    public void handle(PlayerActionPayload payload, IPayloadContext context) {
        Player player = context.player();
        if (player instanceof ServerPlayer serverPlayer) {
            context.enqueueWork(() -> DDZTableManager.getInstance().handle(serverPlayer, payload));
        }
    }
}
