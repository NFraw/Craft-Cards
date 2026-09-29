package com.jokernan.craftycards.game.server;

import com.jokernan.craftycards.CCReference;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.util.TriState;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 牌桌相关的 **NeoForge 事件胶水**。
 *
 * <p>每个处理器只做一件事：把 NeoForge 的事件对象**解包**成原版参数，交给
 * {@link DDZTableManager} 里那份两个加载器共用的逻辑。这样"什么时候该做什么"只有一份实现，
 * 换加载器时只需要重写这个文件（Fabric 侧对应 tick / 连接 / 破坏 / 交互几种回调）。</p>
 */
public final class NeoForgeTableEvents {
    private static final Logger LOG = LoggerFactory.getLogger(CCReference.MOD_ID);

    private NeoForgeTableEvents() {}

    /** 在模组构造函数里调用（服务端 GAME 事件总线）。 */
    public static void register(IEventBus bus) {
        bus.addListener(NeoForgeTableEvents::onServerTick);
        bus.addListener(NeoForgeTableEvents::onPlayerLoggedOut);
        bus.addListener(NeoForgeTableEvents::onBlockBreak);
        bus.addListener(NeoForgeTableEvents::onRightClickBlock);
    }

    private static void onServerTick(ServerTickEvent.Post event) {
        DDZTableManager.getInstance().tick(event.getServer());
    }

    private static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            DDZTableManager.getInstance().handleLogout(player);
        }
    }

    private static void onBlockBreak(BlockEvent.BreakEvent event) {
        if (event.getLevel() instanceof Level level) {
            DDZTableManager.getInstance().handleBlockBreak(level, event.getPos(), event.getState());
        }
    }

    /**
     * 潜行 + 右键牌桌时把交互强制交给方块。
     *
     * <p>何时该强制由 {@link DDZTableManager#shouldForceBlockUse} 回答（common，有单测），
     * 这里只负责把结果写回 NeoForge 的事件 —— 也正是"common 只回答该不该、改事件是胶水的事"
     * 那句话的落点。</p>
     *
     * <p>测试可见性：{@code DDZChipBettingGameTest} 直接构造事件调这个方法（GameTest 里没有
     * 真正的右键输入），所以它必须是 public。</p>
     */
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        boolean force = DDZTableManager.shouldForceBlockUse(
            event.getEntity(), event.getLevel().getBlockState(event.getPos()));
        if (force) {
            event.setUseBlock(TriState.TRUE);
        }
    }
}
