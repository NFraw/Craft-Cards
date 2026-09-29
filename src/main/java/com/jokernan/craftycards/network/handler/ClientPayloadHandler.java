package com.jokernan.craftycards.network.handler;

import com.jokernan.craftycards.client.ClientDDZData;
import com.jokernan.craftycards.network.payload.GameStatePayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.handling.IPayloadHandler;

/**
 * 客户端载荷处理器 — 接收服务端广播的 {@link GameStatePayload} 快照。
 * <p>
 * 在客户端网络线程收到 S2C 包后调用，通过 {@code enqueueWork} 将处理逻辑
 * 调度到客户端主线程执行（避免线程安全问题）。
 * </p>
 *
 * @see ClientDDZData#apply 更新客户端缓存状态
 * @see DDZNetworking#registerPayloads 注册入口
 */
public class ClientPayloadHandler implements IPayloadHandler<GameStatePayload> {
    /**
     * 处理服务端发送的游戏状态快照。
     * <p>
     * 将快照数据传递给 {@link ClientDDZData} 更新客户端缓存，
     * 由 HUD 和世界内渲染器读取并渲染。
     * </p>
     *
     * @param payload 服务端广播的游戏状态快照
     * @param context 网络上下文（用于调度到主线程）
     */
    @Override
    public void handle(GameStatePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> ClientDDZData.getInstance().apply(payload));
    }
}
