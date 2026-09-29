package com.jokernan.craftycards.game.server;

import com.jokernan.craftycards.block.BlockDDZTable;
import com.jokernan.craftycards.game.DDZInteraction;
import com.jokernan.craftycards.network.payload.PlayerActionPayload;
import com.jokernan.craftycards.network.payload.TableKey;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * 全局斗地主牌桌管理器：每张桌（TableKey=维度+坐标）一个 {@link DDZSession}。
 *
 * <p><b>这个类是"逻辑在 common、事件在加载器"的样板</b>：它自己不认识任何加载器的事件对象，
 * 只提供普通方法（{@link #tick(MinecraftServer)} / {@link #handleLogout} /
 * {@link #handleBlockBreak} / {@link #shouldForceBlockUse}），由各加载器的胶水把事件解包后调进来
 * （NeoForge 侧是 {@code game/server/NeoForgeTableEvents}；Fabric 侧对应
 * {@code ServerTickEvents} / {@code ServerPlayConnectionEvents} / 方块破坏与交互回调）。
 * 参数一律用原版类型，所以同一份逻辑两边原样共用。</p>
 */
public class DDZTableManager {
    /** 空闲检查的节拍（游戏刻）：每秒查一次足够，不必每刻遍历。 */
    private static final int IDLE_CHECK_INTERVAL = 20;

    /**
     * 每轮限时与配置锁的检查节拍（游戏刻）：4 次/秒。
     *
     * <p>比空闲销毁细是有理由的：这两件事都直接写在玩家眼前——HUD 的每轮倒计时走到 0 就该判负、
     * 配置锁超时该把界面收掉。按秒查会变成"倒计时已经 0 了还等一秒才判负"，看起来像卡住。</p>
     */
    private static final int TIMEOUT_CHECK_INTERVAL = 5;

    private static final DDZTableManager INSTANCE = new DDZTableManager();
    private final Map<TableKey, DDZSession> sessions = new ConcurrentHashMap<>();

    public static DDZTableManager getInstance() {
        return INSTANCE;
    }

    /**
     * 服务端每刻的清扫（由各加载器的 tick 事件驱动）。
     *
     * <p>三件事：每轮限时判负 + 配置锁超时（4 次/秒）、空闲销毁（每秒）、
     * 给牌桌旁旁观者补发快照（间隔见 {@link ServerGameConfig#spectateRefreshTicks}）。
     * 用服务端 tick 计数取模，不额外维护计数器；没有旁观者在范围内时只做距离判定，开销可忽略。</p>
     */
    public void tick(MinecraftServer server) {
        if (sessions.isEmpty()) return;
        long tick = server.getTickCount();

        // 每轮限时：轮到的玩家超时没动作就判负（独立于下面的空闲销毁，后者是「整桌没人动」的兜底）
        if (tick % TIMEOUT_CHECK_INTERVAL == 0) {
            for (DDZSession s : List.copyOf(sessions.values())) {
                s.tickTurnTimeout(tick, this);
                // 配置锁：界面开着人走了/超时了就放锁，否则别人永远打不开这张桌
                s.tickConfigLock(tick, server);
            }
        }

        // 空闲清扫：每秒查一次，超过配置时长没有任何操作就销毁牌局（回收占位物品、杜绝长期占用）
        int timeout = ServerGameConfig.idleTimeoutTicks;
        if (timeout > 0 && tick % IDLE_CHECK_INTERVAL == 0) {
            for (DDZSession s : List.copyOf(sessions.values())) {
                if (tick - s.getLastActivityTick() > timeout) {
                    s.disband("长时间无人出牌，牌局已结束", this);
                }
            }
        }

        int interval = ServerGameConfig.spectateRefreshTicks;
        if (interval <= 0 || tick % interval != 0) return;
        for (DDZSession s : List.copyOf(sessions.values())) {
            s.refreshSpectators();
        }
    }

    /**
     * 玩家掉线/退出：把他在每张桌上的身份都摘干净。
     *
     * <p>观战者也要走 {@code handleLeave}（它顺带把观战集合与配置锁摘掉）；
     * 正开着配置界面的人可能既没座位也没观战，所以房主/持锁者也要照顾到。</p>
     */
    public void handleLogout(ServerPlayer player) {
        for (DDZSession s : List.copyOf(sessions.values())) {
            if (s.contains(player) || s.isWatching(player) || s.isConfigLocker(player)) {
                s.handleLeave(player, this);
            }
        }
    }

    /** 牌桌方块被拆：以被拆位置为桌标识解散牌局（1×1 单方块，不需要搜多方块结构）。 */
    public void handleBlockBreak(Level level, BlockPos pos, BlockState state) {
        if (!(state.getBlock() instanceof BlockDDZTable)) return;
        TableKey key = new TableKey(level.dimension(), pos);
        DDZSession session = sessions.get(key);
        if (session != null) session.disband("牌桌被拆除了", this);
    }

    /**
     * 潜行 + 右键牌桌时，**该不该把交互强制交给方块**（调用方据此把自己的事件设成"使用方块"）。
     *
     * <p>不加这一手的话，Minecraft 会把右键让给手持物品，方块根本收不到——
     * 于是"Shift+右键打开本桌配置界面 / 开关观战"与"参局者手持扑克时 Shift+右键离开"都失效。
     * 判断规则见 {@link DDZInteraction#shouldForceBlockUse}（纯函数，有单测）。</p>
     *
     * <p>返回布尔值而不是直接改事件：两个加载器的交互事件类型完全不同，
     * common 只回答"该不该"，改事件是胶水的事。</p>
     */
    public static boolean shouldForceBlockUse(Player player, BlockState state) {
        return DDZInteraction.shouldForceBlockUse(
            player.isSecondaryUseActive(),
            player.getMainHandItem().isEmpty(),
            player.getOffhandItem().isEmpty(),
            state.getBlock() instanceof BlockDDZTable);
    }

    public void handle(ServerPlayer player, PlayerActionPayload payload) {
        // 记一次活跃：推迟空闲超时。只认**牌局中玩家**的操作——否则任何路人反复
        // 开关观战就能把"空闲销毁"无限推迟，局内三人挂机时牌局（以及每个客户端的
        // 牌面渲染）永远回收不掉，这正是 idleTimeoutTicks 要防的事。
        DDZSession session = sessions.get(payload.table());
        if (session != null && session.contains(player)) session.touch(player);
        switch (payload.action()) {
            case JOIN -> join(player, payload.table());
            case BID -> withSession(payload.table(), s -> s.handleBid(player, payload.score()));
            case PLAY -> withSession(payload.table(), s -> s.handlePlay(player, payload.cards()));
            case PASS -> withSession(payload.table(), s -> s.handlePass(player));
            case LEAVE -> withSession(payload.table(), s -> s.handleLeave(player, this));
            case WATCH -> watch(player, payload.table());
            case OPEN_TABLE_CONFIG -> configSession(payload.table(), player).handleOpenConfig(player, this);
            // 保存与关闭都**先建会话**：配置界面开着的时候会话可能已被空闲销毁摘掉，
            // 用"找不到会话就静默什么都不做"处理，玩家点了保存却毫无反应，是最难排查的一种失败
            case SAVE_TABLE_CONFIG -> configSession(payload.table(), player).handleSaveConfig(player,
                payload.chipItem(), payload.score(), firstOr(payload.cards(), 0), payload.chipsEnabled(), this);
            case CLOSE_TABLE_CONFIG -> configSession(payload.table(), player).handleCloseConfig(player);
        }
    }

    /**
     * 取本桌会话，不存在就建一个。
     *
     * <p>配置动作（打开/保存/关闭）都走这里：打开时本来就还没有任何人入局、会话可能还不存在，
     * 而保存/关闭时可能刚被空闲销毁摘掉。建出来的空会话不占资源，空闲超时会照常回收。</p>
     */
    private DDZSession configSession(TableKey key, ServerPlayer player) {
        long now = player.serverLevel().getServer().getTickCount();
        return sessions.computeIfAbsent(key, k -> new DDZSession(k, now));
    }

    /** 列表的第一个元素，空表取默认值（配置包的"门槛"塞在 cards 里）。 */
    private static int firstOr(List<Integer> list, int fallback) {
        return list == null || list.isEmpty() ? fallback : list.get(0);
    }

    /** 观战开关：牌桌不存在时给出提示，而不是静默无响应。 */
    private void watch(ServerPlayer player, TableKey key) {
        DDZSession session = sessions.get(key);
        if (session == null) {
            player.sendSystemMessage(Component.translatable("message.ddz.nothing_to_watch"));
            return;
        }
        session.handleWatch(player);
    }

    public void join(ServerPlayer player, TableKey key) {
        long now = player.serverLevel().getServer().getTickCount();
        DDZSession session = sessions.computeIfAbsent(key, k -> new DDZSession(k, now));
        session.touch(player);
        session.handleJoin(player);
    }

    public void remove(TableKey key) {
        sessions.remove(key);
    }

    /**
     * 给所有牌局的在座玩家与观战者重发一份快照。
     *
     * <p>玩法配置改掉之后调用（见 {@code ServerGameConfig#apply}）：单机/局域网里配置是同一个 JVM
     * 的静态字段，改完立刻生效，但客户端<b>看不到</b>——它只认服务端下发的快照，而座上玩家平时
     * 收不到周期性补发。不推这一下，就会看到"每轮限时都关了，倒计时还冻在 0s"这种界面与配置
     * 不一致的现象。连专用服务器时配置本来就不在客户端改，这里是空转（sessions 为空）。</p>
     */
    public void refreshAll() {
        for (DDZSession s : List.copyOf(sessions.values())) {
            s.refresh();
        }
    }

    /** 取某张桌的会话；不存在返回 null。供测试与调试查询牌局状态。 */
    public DDZSession session(TableKey key) {
        return sessions.get(key);
    }

    private void withSession(TableKey key, Consumer<DDZSession> consumer) {
        DDZSession session = sessions.get(key);
        if (session != null) consumer.accept(session);
    }
}
