package com.jokernan.craftycards.fabric;

import com.jokernan.craftycards.game.server.DDZTableManager;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.level.ServerLevel;

/**
 * Fabric 侧的**服务端事件胶水** —— 与 NeoForge 的 {@code NeoForgeTableEvents} 一一对应。
 *
 * <p>每个回调只做一件事：把事件参数解包成原版类型，交给 common 的 {@link DDZTableManager}
 * （逻辑只有那一份）。NeoForge 用 {@code @SubscribeEvent} + 事件对象，
 * Fabric 用几个 API 回调——差别仅此而已。</p>
 *
 * <p>潜行手持物品的交互由客户端 UseBlockCallback 接入；它先于原版的潜行分支执行，
 * 发出正常 C2S 操作后消费交互，最终权限仍由服务端校验。</p>
 */
public final class FabricServerEvents {
    private FabricServerEvents() {}

    /** 在模组入口调用。 */
    public static void register() {
        // 每刻清扫：每轮限时判负 + 配置锁 + 空闲销毁 + 旁观快照补发
        ServerTickEvents.END_SERVER_TICK.register(server -> DDZTableManager.getInstance().tick(server));

        // 掉线：把他在每张桌上的身份摘干净（座位/观战/配置锁）
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) ->
            DDZTableManager.getInstance().handleLogout(handler.getPlayer()));

        // 拆桌：以被拆位置为桌标识解散牌局
        PlayerBlockBreakEvents.AFTER.register((world, player, pos, state, blockEntity) -> {
            if (world instanceof ServerLevel level) {
                DDZTableManager.getInstance().handleBlockBreak(level, pos, state);
            }
        });
    }
}
