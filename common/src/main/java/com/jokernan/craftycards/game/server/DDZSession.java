package com.jokernan.craftycards.game.server;

import com.jokernan.craftycards.CCReference;
import com.jokernan.craftycards.blockentity.DDZTableBlockEntity;
import com.jokernan.craftycards.game.DDZEngine;
import com.jokernan.craftycards.game.DDZGamePhase;
import com.jokernan.craftycards.game.DDZGeom;
import com.jokernan.craftycards.game.DDZVisibility;
import com.jokernan.craftycards.network.payload.GameStatePayload;
import com.jokernan.craftycards.network.payload.TableKey;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import com.jokernan.craftycards.init.InitItems;
import com.jokernan.craftycards.platform.Network;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/**
 * 一张斗地主桌的牌局会话（服务端权威）。
 * 3 个座位绑定真实玩家，所有牌局逻辑由 {@link DDZEngine} 执行，
 * 每次操作后向同桌 3 人广播各自的全量状态快照，并向桌边旁观者广播只读快照。
 *
 * <p>玩家入局后<b>不绑定座椅实体</b>，可自由走动、凑近看桌面上的牌；
 * 主动离开方式是 Shift+右键牌桌（{@code handleLeave}），掉线/被拆桌仍照旧清退。</p>
 */
public class DDZSession {
    private final TableKey key;
    private final ServerPlayer[] seats = new ServerPlayer[DDZEngine.PLAYER_COUNT];
    private final DDZEngine engine = new DDZEngine();
    private boolean closed = false;

    /**
     * 本桌的筹码配置（筹码类型／底注／门槛／本桌开关）都在<b>方块实体</b>里，不在这里。
     *
     * <p>这里只留一个懒加载的引用：会话按 {@link TableKey} 建立，可能早于方块实体被访问
     * （第一个 Shift+右键配置的人还没入座，{@code seats} 全空、拿不到 {@code ServerLevel}），
     * 所以取用时必须带上"任意一名在场的玩家"来定位维度，拿不到就退回全局默认——异常与测试
     * 情况下不能崩，但也不能假装每桌配置生效了。</p>
     */
    @Nullable private DDZTableBlockEntity be;

    /** 赌注：物品池——每位玩家押上的原物（下标 = 座位，每人 {@link #stake()} 份）。
     *
     * <p>存 ItemStack 而不是物品类型：只记注册名再 {@code new ItemStack} 重建会抹掉
     * **附魔、自定义名称、耐久等全部组件**（押附魔剑拿回白板剑）。押上的原物用
     * {@code split()} 取出，组件完整保留，结算/退还时原样交还。</p>
     *
     * <p>用 List 而不是单个 ItemStack，是因为底注可能多于 1 份，而多份可能来自不同格、
     * 各自带着不同组件，合并成一个堆会把它们混掉。</p>
     */
    private final List<ItemStack>[] betStakes = new List[DDZEngine.PLAYER_COUNT];

    {
        for (int i = 0; i < betStakes.length; i++) betStakes[i] = new ArrayList<>();
    }
    /** 赌注：每人是否已押注（按座位索引） */
    private final boolean[] betPlaced = new boolean[DDZEngine.PLAYER_COUNT];

    /**
     * 主动观战者（右键牌桌开启，见 {@link #handleJoin}）：不受 {@code spectateRadius} 距离限制、
     * 持续收到只读快照。存 UUID 而不是玩家引用，掉线后不会留住实体。
     */
    private final java.util.Set<UUID> watchers = new java.util.HashSet<>();

    /**
     * 上一位出牌者出牌那一刻，"牌桌中心 → 他"的朝向角（度）。
     * 玩家可自由走动，所以这个方向必须在出牌瞬间定格并随快照下发；
     * 客户端若按实时位置算，牌会跟着出牌者绕桌转圈。
     */
    private float lastPlayedYaw = 0F;

    /** 最近一次有效操作的服务器刻；超过 {@link ServerGameConfig#idleTimeoutTicks} 未操作即销毁牌局。 */
    private long lastActivityTick;

    public DDZSession(TableKey key, long nowTick) {
        this.key = key;
        this.lastActivityTick = nowTick;
    }

    // === 本桌配置（都走方块实体；拿不到就退回全局默认，绝不崩） ===

    /**
     * 取本桌的方块实体：{@code any} 用来定位维度与关卡。
     *
     * <p>传入的 {@code any} 优先，其次是任一在座玩家（快照/结算这类没有"操作者"的路径）。
     * 首次拿到时会 {@link DDZTableBlockEntity#ensureSeeded()}——老存档里的桌子就是这样补上
     * 默认值的，不需要迁移脚本。</p>
     *
     * <p>取不到时不返回 null 而是回退到上一次拿到的实例：区块卸载重建会换一个实例，
     * 但这种时候牌局本身也该结束了；留着旧引用最多是配置读到旧值，比 NPE 好。</p>
     */
    @Nullable
    private DDZTableBlockEntity tableConfig(@Nullable ServerPlayer any) {
        ServerLevel level = any != null ? any.serverLevel() : serverLevel();
        if (level != null && level.dimension().equals(key.dimension())
            && level.getBlockEntity(key.pos()) instanceof DDZTableBlockEntity table) {
            table.ensureSeeded();
            be = table;
            return table;
        }
        return be;
    }

    /**
     * 本桌底注（每人入场押注数）。没有方块实体时退回全局 {@link ServerGameConfig#chipStake}。
     */
    public int stake() {
        DDZTableBlockEntity table = tableConfig(null);
        return table == null ? Math.max(1, ServerGameConfig.chipStake) : table.stake();
    }

    /**
     * 本桌入局门槛（背包里要备够几个才准入）。<b>0 = 本桌不玩筹码</b>，两种情形：
     * 全局闸门（{@code server.json} 的 {@code chipsRequired}）关掉，或本桌自己的开关关掉。
     *
     * <p>沿用 {@code ServerGameConfig.requiredChips()} 的约定：用 0 而不是另加布尔字段表示
     * "不玩赌注"，快照下发 0、客户端据此把界面切成"无需筹码"。</p>
     */
    public int requiredChips() {
        if (!ServerGameConfig.chipsRequired) return 0;
        DDZTableBlockEntity table = tableConfig(null);
        if (table == null) return ServerGameConfig.requiredChips();
        return table.chipsEnabled() ? table.effectiveEntryCount() : 0;
    }

    /**
     * 本桌<b>自己</b>的筹码开关（原始值，不受全局闸门影响）。
     *
     * <p>配置界面要用它显示/编辑这一项：闸门关掉时 {@link #requiredChips()} 也是 0，
     * 只看那个数字分不清"服务器不让用筹码"还是"这张桌自己关了"。</p>
     */
    public boolean tableChipsEnabled() {
        DDZTableBlockEntity table = tableConfig(null);
        return table == null || table.chipsEnabled();
    }

    /** 本桌筹码物品的注册名（如 {@code minecraft:diamond}）；空串 = 这一桌还没配。 */
    public String chipItemName() {
        DDZTableBlockEntity table = tableConfig(null);
        return table == null ? "" : table.chipItem();
    }

    /** 本桌的方块实体（供管理器清理配置锁等；拿不到返回 null）。 */
    @Nullable
    DDZTableBlockEntity tableEntity(@Nullable ServerPlayer any) {
        return tableConfig(any);
    }

    /** 记一次有效操作（入局/叫分/出牌/过牌/离开），用于空闲超时判定。 */
    public void touch(ServerPlayer player) {
        this.lastActivityTick = player.serverLevel().getServer().getTickCount();
    }

    /** 最近一次操作发生的服务器刻。 */
    public long getLastActivityTick() {
        return lastActivityTick;
    }

    public TableKey getKey() {
        return key;
    }

    public int seatOf(UUID uuid) {
        for (int i = 0; i < seats.length; i++) {
            if (seats[i] != null && seats[i].getUUID().equals(uuid)) return i;
        }
        return -1;
    }

    public boolean contains(ServerPlayer player) {
        return seatOf(player.getUUID()) >= 0;
    }

    /** 当前牌局阶段（只读，供测试与调试断言）。 */
    public DDZGamePhase phase() {
        return engine.getPhase();
    }

    /** 指定座位的手牌张数（只读，供测试与调试断言）。 */
    public int handSize(int seat) {
        return engine.getPlayerHand(seat).size();
    }

    /** 当前该谁行动（座位索引，-1 = 无）。只读，供测试与调试断言。 */
    public int currentSeat() {
        return engine.getCurrentPlayerIndex();
    }

    /** 地主座位（-1 = 未定）。只读，供测试与调试断言。 */
    public int landlordSeat() {
        return engine.getLandlordIndex();
    }

    /**
     * 取某个接收者实际会收到的那份快照（只读，供测试与调试）。
     *
     * <p>快照是"按接收者单独构建"的，防御性行为（谁是参与者/旁观者、能看到哪些牌面）
     * 全在这一层决定，故测试必须能拿到它——否则"防作弊"这条只能靠读代码确认。</p>
     */
    public GameStatePayload snapshotFor(ServerPlayer viewer) {
        return buildSnapshot(viewer, "");
    }

    /**
     * 本局单个农民的得失分：{@code 底分 × 倍数 × 底注}（地主为其两倍）。
     * 显示与测试都从这里取，避免各处各算一遍算不一致。
     */
    public int scorePerFarmer() {
        return engine.getScorePerFarmer() * stake();
    }

    /** 底牌张数（只读，供测试与调试断言）。 */
    public int dipaiSize() {
        return engine.getDipai().size();
    }

    public int playerCount() {
        int count = 0;
        for (ServerPlayer p : seats) if (p != null) count++;
        return count;
    }

    public void handleJoin(ServerPlayer player) {
        if (closed) {
            sendFeedback(player, "message.ddz.closed");
            return;
        }
        // 服务端侧校验距离与维度：不依赖客户端自觉，也挡住"带任意坐标发 JOIN"的改包行为
        if (!withinReach(player)) {
            sendFeedback(player, "message.ddz.too_far");
            return;
        }
        if (contains(player)) {
            // 已入座：补发一次牌面数据，顺手补上丢失的斗地主扑克（丢进岩浆等情况下不至于卡死）
            ensureCard(player);
            sendSnapshot(player);
            return;
        }
        // 本桌的筹码配置在方块实体里（房主用 Shift+右键的配置界面改）：先把它读出来（顺带种入默认值），
        // 后面所有审核与实收都以本桌为准。
        // 这一步**必须先做**：此刻还没人入座，没有玩家就定位不到关卡（{@code serverLevel()} 返回 null），
        // 后面按"本桌"读到的会是全局默认值——而"每桌独立"正是要避免这件事。
        tableConfig(player);
        // need == 0 表示本桌不玩筹码（全局闸门关掉，或本桌自己关了）：整个筹码环节跳过
        int need = requiredChips();
        net.minecraft.world.item.Item chip = null;
        if (need > 0) {
            if (chipItemName().isEmpty()) {
                sendFeedback(player, "message.ddz.chip_not_set");
                return;
            }
            chip = chipItem();
            if (chip == null) {
                sendFeedback(player, "message.ddz.chip_invalid");
                return;
            }
        }

        int seat = firstEmptySeat();
        if (seat < 0) {
            // 没空位：右键牌桌改为"开始/停止观战"。分两种情况——牌局开局前加入过服务器的玩家
            // 不会自动收到牌面数据（要么离得远、要么根本没走到桌前），右键一次就立刻拿一份并持续更新
            toggleWatch(player);
            return;
        }

        if (need > 0) {
            // 门槛：背包（含副手与护甲槽）里必须够数，保证本局赔得起。
            // 不够就拒绝参与，并明确告知还差多少——否则玩家只会觉得"右键没反应"
            int have = countOfType(player, chip);
            if (have < need) {
                player.sendSystemMessage(Component.translatable("message.ddz.chip_not_enough", need, have));
                return;
            }
        }

        seats[seat] = player;
        collectBet(player);
        ensureCard(player);
        // 三人坐满自动开局（发牌进入叫分）；没满只广播座位状态
        if (isFull()) engine.shuffleAndDeal();
        broadcast();
    }

    /** 该玩家是否在主动观战。 */
    public boolean isWatching(ServerPlayer player) {
        return watchers.contains(player.getUUID());
    }

    /**
     * Shift+右键：未入局玩家的观战开关（不占座位、不必等坐满）。
     * <p>这是"非参局玩家看牌"的正规入口——不依赖距离自动共享，故公共服务器可以安全地
     * 把 {@code spectateRadius} 保持为 0。参局者的 Shift+右键是离开，走 {@link #handleLeave}。</p>
     */
    public void handleWatch(ServerPlayer player) {
        if (closed) {
            sendFeedback(player, "message.ddz.closed");
            return;
        }
        if (contains(player)) return;
        if (!withinReach(player)) {
            sendFeedback(player, "message.ddz.too_far");
            return;
        }
        toggleWatch(player);
    }

    /**
     * 切换主动观战：开启时立刻发一份只读快照并加入集合（此后不受距离限制持续更新）；
     * 关闭时用 {@code closed = true} 的快照通知客户端清空状态（旁观者收到该标志会静默 reset）。
     *
     * <p>注意"停止"只关掉<b>远程推送</b>：站在 {@link ServerGameConfig#spectateRadius} 内的玩家
     * 本来就会自动收到快照（"走到桌边就能看"是设计使然），这一点不因停止观战而改变。
     * 反馈消息里已向玩家说明，避免他以为关掉了却还在桌边看到牌。
     */
    private void toggleWatch(ServerPlayer player) {
        UUID id = player.getUUID();
        if (watchers.remove(id)) {
            sendQuietly(player, buildSnapshot(player, "", true));
            sendFeedback(player, "message.ddz.watch_stopped");
        } else {
            watchers.add(id);
            sendQuietly(player, buildSnapshot(player, "", false));
            sendFeedback(player, "message.ddz.watch_started");
        }
    }

    // === 房主与配置界面（Shift+右键牌桌） ===

    /** 配置锁的闲置上限（游戏刻）：界面开着人走了，超过它就自动释放，别人才能配这张桌。 */
    private static final int CONFIG_LOCK_TIMEOUT_TICKS = 120 * 20;

    /**
     * Shift+右键牌桌 = 打开本桌配置界面（只对房主开放）。
     *
     * <p>房主 = <b>第一个</b> Shift+右键这张桌的玩家（记在方块实体里，随桌子持久化）。
     * 原房主<b>离线</b>时允许后来的玩家接管——否则房主一走，这张桌子就再没人配得了
     * （筹码没配的桌子谁都入不了局，等于砖了）。</p>
     *
     * <p>已有在线房主且不是本人时，Shift+右键退回它原来的用途——观战开关
     * （见 {@link #handleWatch}）：观战没有别的入口，不能因为多了配置界面就把它挤掉。</p>
     *
     * <p>只有局外（还没人入座）能改配置：入局时就按当时的底注收了押注，中途改底注会让
     * 先入局的人吃亏。</p>
     */
    public void handleOpenConfig(ServerPlayer player, DDZTableManager manager) {
        if (closed) {
            sendFeedback(player, "message.ddz.closed");
            return;
        }
        if (!withinReach(player)) {
            sendFeedback(player, "message.ddz.too_far");
            return;
        }
        DDZTableBlockEntity table = tableConfig(player);
        if (table == null) {
            sendFeedback(player, "message.ddz.config_no_table");
            return;
        }
        if (engine.getPhase() != DDZGamePhase.WAITING || playerCount() > 0) {
            sendFeedback(player, "message.ddz.config_in_game");
            return;
        }
        long tick = player.serverLevel().getServer().getTickCount();
        if (!table.isHost(player.getUUID())) {
            if (table.host() == null || hostOffline(player, table)) {
                table.setHost(player.getUUID(), player.getGameProfile().getName());
                sendFeedback(player, "message.ddz.config_host_taken");
            } else {
                sendFeedback(player, "message.ddz.config_not_host", hostDisplayName(table));
                // 不是房主：Shift+右键回到"观战开关"这个原有用途
                handleWatch(player);
                return;
            }
        }
        // 锁被别人握着（且人还在、没走远、没超时）就拒绝：同一时间只允许一个人开界面。
        // 陈旧锁就地收回——否则一把没人持有的锁能把这张桌锁到天荒地老（会话可能早已被摘掉，
        // 清扫也就不再跑了，这里必须自己兜住）
        if (table.isLockedByOther(player.getUUID())) {
            ServerPlayer other = player.server.getPlayerList().getPlayer(table.configLocker());
            if (other != null && !configLockExpired(other, table, tick)) {
                sendFeedback(player, "message.ddz.config_busy", lockerName(player, table));
                return;
            }
            table.clearConfigLocker();
        }
        table.setConfigLocker(player.getUUID(), tick);
        sendSnapshot(player);
    }

    /**
     * 保存本桌配置（配置界面的「保存」）。
     *
     * <p>服务端<b>全量重校验</b>：房主身份、是否还握着锁、是否还有人在局中、筹码物品是否在
     * 服务端白名单里。客户端读的是它自己那份 {@code server.json}，连专用服务器时与服端无关，
     * 所以白名单必须在服务端再验一遍——否则改过的客户端能把任意物品设成本桌筹码。</p>
     *
     * <p>回执走快照的 {@code feedback}：配置界面开着的时候聊天栏是看不见的（HUD 整个不画），
     * 界面拿这一句当状态行显示，成功失败都看得见。</p>
     */
    public void handleSaveConfig(ServerPlayer player, String chipItem, int stake, int entryCount,
                                 boolean chipsEnabled, DDZTableManager manager) {
        if (closed) {
            sendFeedback(player, "message.ddz.closed");
            return;
        }
        if (!withinReach(player)) {
            sendFeedback(player, "message.ddz.too_far");
            return;
        }
        DDZTableBlockEntity table = tableConfig(player);
        if (table == null) {
            sendFeedback(player, "message.ddz.config_no_table");
            return;
        }
        if (!table.isHost(player.getUUID())) {
            sendFeedback(player, "message.ddz.config_not_host", hostDisplayName(table));
            return;
        }
        if (!table.isConfigLocker(player.getUUID())) {
            sendFeedback(player, "message.ddz.config_timeout");
            return;
        }
        if (engine.getPhase() != DDZGamePhase.WAITING || playerCount() > 0) {
            sendFeedback(player, "message.ddz.config_in_game");
            return;
        }
        String id = chipItem == null ? "" : chipItem;
        if (!id.isEmpty() && !ServerGameConfig.chipItems.contains(id)) {
            sendConfigResult(player, "筹码物品不在服务器的白名单里（本机配置与服务器不同？）");
            return;
        }
        table.setChipItem(id);
        table.setStake(Math.max(1, stake));
        table.setEntryCount(Math.max(0, entryCount));
        table.setChipsEnabled(chipsEnabled);
        table.setConfigLocker(player.getUUID(), player.serverLevel().getServer().getTickCount());
        // 桌边的人（观战者/后续入局者）也要看到新的底注与门槛
        broadcast();
        sendConfigResult(player, "已保存：" + describeConfig(table));
    }

    /** 关闭配置界面：只清自己持有的锁（别人的锁不碰）。 */
    public void handleCloseConfig(ServerPlayer player) {
        DDZTableBlockEntity table = tableConfig(player);
        if (table != null && table.isConfigLocker(player.getUUID())) {
            table.clearConfigLocker();
        }
    }

    /** 配置锁是否握在这名玩家手里（掉线清理要用）。 */
    public boolean isConfigLocker(ServerPlayer player) {
        DDZTableBlockEntity table = tableConfig(player);
        return table != null && table.isConfigLocker(player.getUUID());
    }

    /**
     * 配置锁清扫（由 {@link DDZTableManager} 每秒调用）：超时、持有者掉线或走远就释放。
     *
     * <p>持有者还在线时给他补一份快照（{@code configOpen = false}），客户端据此把配置界面关掉——
     * 不然界面会一直开着、点保存只会收到"配置已超时"。</p>
     */
    public void tickConfigLock(long tick, net.minecraft.server.MinecraftServer server) {
        if (closed) return;
        DDZTableBlockEntity table = tableConfig(null);
        if (table == null || table.configLocker() == null) return;
        ServerPlayer locker = server.getPlayerList().getPlayer(table.configLocker());
        if (locker != null && !configLockExpired(locker, table, tick)) return;
        table.clearConfigLocker();
        if (locker != null) {
            sendQuietly(locker, buildSnapshot(locker, "配置界面已自动关闭（超时或走远了），重新 Shift+右键牌桌即可打开"));
        }
    }

    /** 锁是否已经不该继续握着：超过上限，或持有者掉线／走出入局距离。 */
    private boolean configLockExpired(ServerPlayer locker, DDZTableBlockEntity table, long tick) {
        if (tick - table.configLockTick() > CONFIG_LOCK_TIMEOUT_TICKS) return true;
        return !withinReach(locker);
    }

    /** 房主是否已经不在服务器上（允许别人接管这张桌）。 */
    private static boolean hostOffline(ServerPlayer any, DDZTableBlockEntity table) {
        UUID host = table.host();
        return host != null && any.server.getPlayerList().getPlayer(host) == null;
    }

    /** 房主的显示名（离线时也能显示：名字随 UUID 一起存在桌子上）。 */
    private static String hostDisplayName(DDZTableBlockEntity table) {
        String name = table.hostName();
        return name.isEmpty() ? "另一名玩家" : name;
    }

    /** 正在配置本桌的玩家名（锁的持有者按定义在线，查得到）。 */
    private static String lockerName(ServerPlayer any, DDZTableBlockEntity table) {
        UUID locker = table.configLocker();
        if (locker == null) return "另一名玩家";
        ServerPlayer p = any.server.getPlayerList().getPlayer(locker);
        return p == null ? "另一名玩家" : p.getGameProfile().getName();
    }

    /** 本桌配置的一句话摘要（配置界面的状态行与提示都用它）。 */
    private String describeConfig(DDZTableBlockEntity table) {
        if (!table.chipsEnabled()) return "本桌不玩筹码";
        String chip = table.chipItem().isEmpty() ? "筹码未选" : table.chipItem();
        return chip + " · 底注 " + table.stake() + " · 门槛 "
            + (table.entryCount() > 0 ? table.entryCount() + "（手动）" : table.effectiveEntryCount() + "（自动）");
    }

    /** 给配置界面回一句结果（走快照 feedback，界面直接显示）。 */
    private void sendConfigResult(ServerPlayer player, String text) {
        sendQuietly(player, buildSnapshot(player, text));
    }

    /** 本桌筹码对应的物品；未配置或注册表里找不到时返回 null。 */
    private net.minecraft.world.item.Item chipItem() {
        String name = chipItemName();
        if (name.isEmpty()) return null;
        return BuiltInRegistries.ITEM.get(ResourceLocation.parse(name));
    }

    /** 玩家背包（含副手与护甲槽）里某物品的总数。 */
    private static int countOfType(ServerPlayer player, net.minecraft.world.item.Item item) {
        if (player == null || item == null) return 0;
        var inv = player.getInventory();
        int total = 0;
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack st = inv.getItem(i);
            if (st.is(item)) total += st.getCount();
        }
        return total;
    }

    public void handleBid(ServerPlayer player, int score) {
        int seat = seatOf(player.getUUID());
        if (seat < 0 || closed) return;
        if (engine.getPhase() != DDZGamePhase.BIDDING || engine.getCurrentPlayerIndex() != seat) {
            sendFeedback(player, "message.ddz.not_your_turn");
            return;
        }
        if (engine.bid(seat, score)) {
            broadcast();
        } else {
            sendFeedback(player, "message.ddz.invalid_bid");
        }
    }

    public void handlePlay(ServerPlayer player, List<Integer> cards) {
        int seat = seatOf(player.getUUID());
        if (seat < 0 || closed) return;
        if (engine.getPhase() != DDZGamePhase.PLAYING || engine.getCurrentPlayerIndex() != seat) {
            sendFeedback(player, "message.ddz.not_your_turn");
            return;
        }
        if (engine.playCards(seat, cards)) {
            lastPlayedYaw = facingOf(player);
            broadcast();
            if (engine.getPhase() == DDZGamePhase.SETTLED) {
                endGame();
            }
        } else {
            if (System.getProperty("craftycards.smoke") != null)
                System.out.println("POC rejected play: seat=" + seat + " cards=" + cards
                    + " hand=" + engine.getPlayerHand(seat) + " lastBy=" + engine.getLastPlayedBy()
                    + " lastCards=" + engine.getLastPlayedCards());
            sendFeedback(player, "message.ddz.invalid_play");
        }
    }

    /** 结算清退：broadcast 已发出最终快照（含三家剩余手牌），这里清座位并移除本桌。
     *  玩家可自由走动，无需下马清退。 */
    private void endGame() {
        distributeBets();
        reclaimAllCards();
        Arrays.fill(seats, null);
        DDZTableManager.getInstance().remove(key);
    }

    /** 发给玩家一张斗地主扑克；已经有了就不重复发。 */
    private void ensureCard(ServerPlayer player) {
        if (player.getInventory().contains(st -> st.is(InitItems.DDZ_CARD.get()))) return;
        ItemStack card = new ItemStack(InitItems.DDZ_CARD.get());
        if (!player.getInventory().add(card)) player.drop(card, false);
    }

    /**
     * 回收玩家背包里的斗地主扑克（牌局结束/解散、玩家离开时调用）。
     * 只清背包：若被放进箱子等容器，那已经是玩家的处置，不去翻别人的容器。
     */
    private void reclaimCard(ServerPlayer player) {
        if (player == null) return;
        var inv = player.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            if (inv.getItem(i).is(InitItems.DDZ_CARD.get())) inv.setItem(i, ItemStack.EMPTY);
        }
    }

    /** 回收所有在座玩家的斗地主扑克。 */
    private void reclaimAllCards() {
        for (ServerPlayer p : seats) reclaimCard(p);
    }

    /**
     * 把玩家的 1 份筹码收进"物品池"（服务端暂存，等价于放进箱子）。
     *
     * <p>取的是**背包里任意一格**的筹码物品（不要求手持——筹码类型已经由声明定下来了）。
     * 用 {@code split(1)} 取出，物品的全部组件（附魔/自定义名/耐久）原样保留，
     * 结算或退还时交还原物，绝不重建。</p>
     *
     * <p>调用前 {@link #handleJoin} 已校验过数量足够；这里再判一次是防御性的。</p>
     */
    private void collectBet(ServerPlayer player) {
        if (engine.getPhase() != DDZGamePhase.WAITING) return;
        tableConfig(player);                 // 定位本桌（收几个、收什么，都以本桌为准）
        if (requiredChips() <= 0) return;   // 本桌不玩筹码：不收押注
        int seat = seatOf(player.getUUID());
        if (seat < 0 || betPlaced[seat]) return;
        net.minecraft.world.item.Item chip = chipItem();
        if (chip == null) return;

        // 按本桌底注收 N 份进池子（逐个 split，各自保留组件）
        List<ItemStack> pool = new ArrayList<>();
        for (int i = 0; i < stake(); i++) {
            ItemStack one = takeOneOfType(player, chip);
            if (one.isEmpty()) break;
            pool.add(one);
        }
        if (pool.isEmpty()) {
            sendFeedback(player, "message.ddz.chip_not_enough_any");
            return;
        }
        betStakes[seat] = pool;
        betPlaced[seat] = true;
    }

    /**
     * 归还玩家实际押上的那一份物品（原样，组件完整）。
     *
     * <p>{@code player} 可能为 null：{@link #disband} 会遍历整个座位表，而离开者的座位
     * 在解散之前就已经清空了（见 {@link #handleLeave}）。这里必须容错——
     * 曾经因为没有这一行判断，玩家中途退出会在 PlayerLoggedOutEvent 里抛 NPE、
     * 把服务端线程带崩（"Encountered an unexpected exception"）。</p>
     */
    private void returnBet(ServerPlayer player) {
        if (player == null) return;
        int seat = seatOf(player.getUUID());
        if (seat < 0 || !betPlaced[seat]) return;
        for (ItemStack stake : betStakes[seat]) {
            // 走 giveOrDrop：背包满时掉在地上，而不是像 add() 那样静默消失
            giveOrDrop(player, stake);
        }
        betStakes[seat] = new ArrayList<>();
        betPlaced[seat] = false;
    }

    /**
     * 从玩家背包取 1 个指定类型的物品（保留该物品自身的组件）。
     * 用于"地主输了要赔付"时补足与押注同类型的物品；取不到返回空。
     */
    private static ItemStack takeOneOfType(ServerPlayer player, net.minecraft.world.item.Item item) {
        if (player == null || item == null) return ItemStack.EMPTY;
        var inv = player.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack st = inv.getItem(i);
            if (st.is(item)) return st.split(1);
        }
        return ItemStack.EMPTY;
    }

    /**
     * 按市面常见规则结算：{@code 底分 × 倍数 × 底注}，其中底分 = 叫分、
     * 倍数 = {@code 2^(炸弹+火箭数) × 春天系数}（见 {@link DDZEngine#getMultiplier}）。
     *
     * <p>地主一人对两农民，故每位农民的得失是一份、地主是两份。支付顺序是
     * <b>池子 → 输家背包补足</b>：输家押上的原物本来就在池子里，先拿它赔付，
     * 不足的部分再从输家背包里取同类型物品——因为倍数可能让单局输赢远超底注
     * （例如叫 3 分、两个炸弹 = 12 倍）。实在取不到就少付并提示，
     * 这是"用实物结算有倍数赌注"的固有限制。</p>
     *
     * <p>押注不能"先退还给各家、再从背包取赔付"：那样池子在这一步就被清空了，而赔付
     * 只认结算时刻的背包——输家只要在结算前把筹码塞进箱子（或丢在地上）就能一分不赔，
     * 池子里明明一直押着他的东西。赔付走池子优先之后，输家至少赔得出押注本身；
     * 只有赢家自己那份（没被用作赔付）最后原样退还。</p>
     */
    private void distributeBets() {
        if (chipItemName().isEmpty()) return;
        int landlord = engine.getLandlordIndex();
        if (landlord < 0) return;
        net.minecraft.world.item.Item chip = chipItem();
        if (chip == null) return;

        int stake = stake();
        int perFarmer = engine.getScorePerFarmer() * stake;   // 底分 × 倍数 × 底注
        boolean landlordWon = engine.getWinnerTeam() == landlord;

        if (landlordWon) {
            // 每位农民赔 perFarmer 个给地主
            for (int i = 0; i < DDZEngine.PLAYER_COUNT; i++) {
                if (i == landlord || seats[i] == null) continue;
                pay(i, landlord, chip, perFarmer);
            }
        } else {
            // 地主赔 perFarmer 个给每位农民
            for (int i = 0; i < DDZEngine.PLAYER_COUNT; i++) {
                if (i == landlord || seats[i] == null) continue;
                pay(landlord, i, chip, perFarmer);
            }
        }

        // 剩下的押注（赢家自己那份，以及少赔时没被取走的部分）原样退还，并复位押注状态
        for (int i = 0; i < DDZEngine.PLAYER_COUNT; i++) {
            for (ItemStack own : betStakes[i]) giveOrDrop(seats[i], own);
            betStakes[i] = new ArrayList<>();
            betPlaced[i] = false;
        }
    }

    /**
     * 从 {@code payerSeat} 处取 {@code amount} 个筹码给 {@code payeeSeat}。
     *
     * <p>先耗尽他押在池子里的原物（组件完整地转给赢家），再用背包里的同类型物品补足；
     * 取不满就少给并提示。</p>
     */
    private void pay(int payerSeat, int payeeSeat, net.minecraft.world.item.Item chip, int amount) {
        ServerPlayer payer = seats[payerSeat];
        ServerPlayer payee = seats[payeeSeat];
        if (payer == null || payee == null || amount <= 0) return;

        int paid = 0;
        List<ItemStack> pool = betStakes[payerSeat];
        while (paid < amount && !pool.isEmpty()) {
            giveOrDrop(payee, pool.remove(0));
            paid++;
        }
        while (paid < amount) {
            ItemStack one = takeOneOfType(payer, chip);
            if (one.isEmpty()) break;
            giveOrDrop(payee, one);
            paid++;
        }
        if (paid < amount) {
            payer.sendSystemMessage(Component.translatable("message.ddz.bet_unpaid", amount - paid));
        }
    }

    /** 给玩家物品，背包满则掉落。 */
    private void giveOrDrop(ServerPlayer player, ItemStack stack) {
        if (player == null || stack.isEmpty()) return;
        if (!player.getInventory().add(stack)) {
            player.drop(stack, false);
        }
    }

    public void handlePass(ServerPlayer player) {
        int seat = seatOf(player.getUUID());
        if (seat < 0 || closed) return;
        if (engine.getPhase() != DDZGamePhase.PLAYING || engine.getCurrentPlayerIndex() != seat) {
            sendFeedback(player, "message.ddz.not_your_turn");
            return;
        }
        if (engine.pass(seat)) {
            broadcast();
        } else {
            sendFeedback(player, "message.ddz.cannot_pass");
        }
    }

    /**
     * 判负：判负者押在池子里的原物先用于赔付，其余两名玩家各得「入局门槛 / 2」（不论地主农民），
     * 随后按解散收场：退还其余两家自己的押注、回收扑克、给所有还在看这个界面的人发终止快照。
     *
     * <p>赔付复用 {@link #pay}：池子优先，不足再从背包补，实在不够则少赔并提示。
     * 门槛是奇数时向下取整（每人 门槛 / 2，余 1 个不赔），不造出半个物品。</p>
     */
    public void forfeit(ServerPlayer loser, String reason, DDZTableManager manager) {
        if (closed) return;
        int seat = seatOf(loser.getUUID());
        if (seat < 0) return;
        tableConfig(loser);   // 先定位本桌（判负赔付额 = 本桌门槛 / 2）
        int per = Math.max(0, requiredChips() / 2);
        net.minecraft.world.item.Item chip = chipItem();
        if (per > 0 && chip != null) {
            for (int i = 0; i < seats.length; i++) {
                if (i == seat || seats[i] == null) continue;
                pay(seat, i, chip, per);
            }
        }
        disband(loser.getGameProfile().getName() + " " + reason
            + "，按判负结算（其余两家各得 " + per + " 个）", manager);
    }

    /** 上一轮的轮次指纹与起始刻：指纹一变就重新计时（见 tickTurnTimeout）。 */
    private int turnFingerprint = Integer.MIN_VALUE;
    private long turnStartTick;

    /**
     * 当前轮次的指纹：<b>阶段 + 该谁 + 叫分 + 上家出的牌 + 三家手牌总数</b>。
     *
     * <p>任何一次操作都会让它变，所以「两家过牌后原出牌人重新领牌」也能被认成新一轮。
     * 计时与倒计时都靠它判断"这一轮开始了没有"。</p>
     */
    private int turnFingerprintNow() {
        int hands = 0;
        for (int i = 0; i < DDZEngine.PLAYER_COUNT; i++) hands += engine.getPlayerHand(i).size();
        return java.util.Objects.hash(engine.getPhase(), engine.getCurrentPlayerIndex(),
            engine.getBidScore(), engine.getLastPlayedBy(),
            engine.getLastPlayedCards() == null ? 0 : engine.getLastPlayedCards().size(), hands);
    }

    /** 当前轮次是否真的在计时（限时开着 + 处在叫分/出牌阶段 + 有个该动作的人）。 */
    private boolean turnTimingActive() {
        if (ServerGameConfig.turnTimeoutTicks <= 0) return false;
        DDZGamePhase phase = engine.getPhase();
        return engine.getCurrentPlayerIndex() >= 0
            && (phase == DDZGamePhase.BIDDING || phase == DDZGamePhase.PLAYING);
    }

    /**
     * 当前轮次还剩多少刻（给 HUD 画倒计时用）；<b>-1 = 不限时</b>（本服务器关掉了每轮限时）。
     *
     * <p>倒计时的基准是"这一轮的开始刻"（{@link #turnStartTick}），而它由管理器的清扫在发现
     * 轮次指纹变化时记下——所以清扫还没跑到时（指纹刚变、这一轮刚开始）直接返回整个限时，
     * 而不是拿上一轮的起点去算出一个负值。</p>
     */
    public int turnRemainingTicks(long now) {
        if (!turnTimingActive()) return -1;
        int limit = ServerGameConfig.turnTimeoutTicks;
        if (turnFingerprintNow() != turnFingerprint) return limit;   // 这一轮刚开始，清扫还没记指纹
        return (int) Math.max(0, limit - (now - turnStartTick));
    }

    /**
     * 每轮限时（ServerGameConfig#turnTimeoutTicks，0 = 关闭）：轮到的人超时没动作就判负。
     * 由 DDZTableManager 定期清扫（见 {@code TIMEOUT_CHECK_INTERVAL}）。
     *
     * <p>用 {@link #turnFingerprintNow()} 判断这一轮是否换人：指纹一变就重新计时。</p>
     */
    public void tickTurnTimeout(long tick, DDZTableManager manager) {
        if (closed) return;
        if (!turnTimingActive()) {
            turnFingerprint = Integer.MIN_VALUE;
            return;
        }
        int limit = ServerGameConfig.turnTimeoutTicks;
        int fp = turnFingerprintNow();
        if (fp != turnFingerprint) {
            turnFingerprint = fp;
            turnStartTick = tick;
            return;
        }
        if (tick - turnStartTick <= limit) return;
        ServerPlayer who = seats[engine.getCurrentPlayerIndex()];
        if (who != null) forfeit(who, "超时未出牌", manager);
    }

    /**
     * 玩家离开（Shift+右键牌桌 / 掉线）：等待中 = 退自己的押注、清座位；
     * 局中 = 按<b>判负</b>结算（赔其余两家）并解散牌局。
     */
    public void handleLeave(ServerPlayer player, DDZTableManager manager) {
        // 观战者离开/掉线也要摘掉，否则集合会随人数无限增长
        watchers.remove(player.getUUID());
        // 他要是正开着配置界面（还没入座的人也可能持有那把锁），锁必须一起放掉
        handleCloseConfig(player);
        int seat = seatOf(player.getUUID());
        if (seat < 0) return;

        // 局中离开/掉线 = 判负：判负者赔其余两家，不能靠走人把押注拿回去。
        // 只有等待中（WAITING）才是普通离开：退自己的押注、清座位。
        if (engine.getPhase() != DDZGamePhase.WAITING) {
            forfeit(player, "离开了牌局", manager);
            return;
        }
        // 顺序不能动，三步各有理由：
        //  ① 先给离开者发一份"牌局已结束"的快照，**再**清座位：清座位之后 broadcast/disband
        //     都只遍历 seats 里还在的人，不会再发给他，他的客户端就会一直停在牌局界面上；
        //     而此时会话已从管理器移除，拆桌、空闲超时都不会再有任何反应——"游戏关不掉"。
        //  ② 退筹码与回收扑克必须**在清座位之前**：两者都靠 seatOf(uuid) 找自己的位子，
        //     位子清掉后就找不到、直接早退——离开者的押注会被静默吞掉
        //     （GameTest waitingLeaveRefundsOwnStake 钉住这一条）。
        //  ③ 最后才清座位，让后续的 broadcast 只处理还在座的人。
        sendQuietly(player, buildSnapshot(player, "你离开了牌局", true));
        reclaimCard(player);
        returnBet(player);
        seats[seat] = null;
        // 走到这里一定是"等待中离开"（局中离开在上面已按判负收场、整桌解散），
        // 所以只剩两件事：没人了就摘桌，还有人就把新的座位状态广播出去
        if (playerCount() == 0) {
            manager.remove(key);
        } else {
            broadcast();
        }
    }

    /** 解散整桌（局中判负 / 牌桌被拆 / 空闲超时），广播后移除。 */
    public void disband(String reason, DDZTableManager manager) {
        if (closed) return;
        closed = true;
        // 配置锁也必须放掉：会话一走清扫就没人在跑了，锁会永久留在桌子上——那张桌从此谁都配不了
        DDZTableBlockEntity table = tableConfig(null);
        ServerPlayer locker = null;
        if (table != null && table.configLocker() != null) {
            ServerLevel level = serverLevel();
            if (level != null) locker = level.getServer().getPlayerList().getPlayer(table.configLocker());
            table.clearConfigLocker();
        }
        try {
            // 退还各在座玩家的赌注：解散（有人中途离开／拆桌／空闲超时）时若不退，
            // 桌面上的押注物品会凭空消失——原先只有主动离开的那位能拿回自己那份。
            // 座位表里可能有 null（离开者的位子已经清掉，他那份在 handleLeave 里退过了）。
            for (ServerPlayer p : seats) {
                if (p != null) returnBet(p);
            }
            reclaimAllCards();
            for (ServerPlayer p : seats) {
                if (p != null) sendQuietly(p, buildSnapshot(p, reason));
            }
            sendToSpectators(reason);
            // 正开着配置界面的人（没入座、也不是观战者，上面两轮都轮不到他）：也要收到终止快照，
            // 否则他的配置界面会一直开着（这正是"界面卡死"那类问题的同一个坑）
            if (locker != null && !contains(locker) && !watchers.contains(locker.getUUID())) {
                sendQuietly(locker, buildSnapshot(locker, reason));
            }
        } finally {
            // 必须在 finally 里摘桌：单个玩家连接异常不能把清理跳过，否则管理器里会残留
            // 一张 closed=true 的桌子——之后任何人右键这张桌都只会得到"牌局已解散"
            manager.remove(key);
        }
    }

    /**
     * 发送快照，单个玩家的连接异常只记日志、不影响其他玩家与后续清理。
     * 玩家掉线瞬间连接可能已不可用，逐个隔离比让整条广播中断更合理。
     */
    private void sendQuietly(ServerPlayer player, GameStatePayload payload) {
        try {
            // 平台接缝：具体用哪个网络的管道由加载器决定（见 platform/Network）
            Network.sendToPlayer(player, payload);
        } catch (RuntimeException e) {
            CCReference.LOG.warn("向 {} 发送牌局快照失败: {}",
                player.getGameProfile().getName(), e.toString());
        }
    }

    /** 玩家是否在牌桌跟前（同维度 + 距离在 {@link ServerGameConfig#joinRadius} 内）。 */
    private boolean withinReach(ServerPlayer player) {
        if (!player.serverLevel().dimension().equals(key.dimension())) return false;
        return player.distanceToSqr(Vec3.atCenterOf(key.pos()))
            <= ServerGameConfig.joinRadius * ServerGameConfig.joinRadius;
    }

    /** 座位是否坐满（坐满即自动发牌开局）。 */
    private boolean isFull() {
        return playerCount() >= DDZEngine.PLAYER_COUNT;
    }

    private int firstEmptySeat() {
        for (int i = 0; i < seats.length; i++) {
            if (seats[i] == null) return i;
        }
        return -1;
    }

    private void broadcast() {
        for (ServerPlayer p : seats) {
            if (p != null) sendQuietly(p, buildSnapshot(p, ""));
        }
        sendToSpectators("");
    }

    /**
     * 给桌边旁观者补发一次快照（不改动任何牌局状态）。由 {@link DDZTableManager} 定期调用：
     * 观众进场时可能长时间没有操作，没有补发就要干等到下一次出牌才看得到画面。
     */
    public void refreshSpectators() {
        if (closed) return;
        sendToSpectators("");
    }

    /**
     * 给<b>在座玩家与观战者</b>都重发一份快照（不改动牌局状态）。
     *
     * <p>用在"玩法配置刚被改掉"的时候：快照里的底注/门槛/倒计时都是按当时的配置算的，
     * 而座上的玩家平时<b>收不到周期性补发</b>（那种补发只给旁观者），于是他们会一直看着旧值——
     * 表现是"我把每轮限时关了，倒计时还冻在 0s"、"门槛改了但提示没变"，直到下一次出牌才更新。</p>
     */
    public void refresh() {
        if (closed) return;
        broadcast();
    }

    private void sendSnapshot(ServerPlayer player) {
        sendQuietly(player, buildSnapshot(player, ""));
    }

    /**
     * 向旁观者广播只读快照（myIndex = -1，无操作权限）。两类接收者：
     * <ul>
     *   <li>牌桌附近（{@link ServerGameConfig#spectateRadius} 内）的玩家——走过去就能看</li>
     *   <li>{@link #watchers 主动观战者}——右键牌桌开启，不受距离限制</li>
     * </ul>
     * 半径设为 0 表示关闭旁观（自动与主动都不发）。谁能看到牌面由
     * {@link ServerGameConfig#spectatorsSeeCards} 决定。
     */
    private void sendToSpectators(String reason) {
        ServerLevel level = serverLevel();
        if (level == null) return;
        // 半径只约束"走近就自动共享"这条可选路径（默认关闭）；主动观战者（右键开启）始终收得到，
        // 否则把半径设成 0 追求安全时会把观战功能一起关掉
        boolean autoShare = ServerGameConfig.spectateRadius > 0;
        double r2 = autoShare ? ServerGameConfig.spectateRadius * ServerGameConfig.spectateRadius : 0;
        Vec3 center = Vec3.atCenterOf(key.pos());
        for (ServerPlayer p : level.players()) {
            if (contains(p)) continue;
            boolean near = autoShare && p.distanceToSqr(center) <= r2;
            if (!DDZVisibility.spectatorReceives(watchers.contains(p.getUUID()), near, autoShare)) continue;
            sendQuietly(p, buildSnapshot(p, reason));
        }
    }

    private ServerLevel serverLevel() {
        for (ServerPlayer p : seats) {
            if (p != null) return p.serverLevel();
        }
        return null;
    }

    /** 玩家相对牌桌中心的水平朝向角（牌桌中心 → 玩家）。 */
    private float facingOf(ServerPlayer player) {
        double dx = player.getX() - (key.pos().getX() + 0.5);
        double dz = player.getZ() - (key.pos().getZ() + 0.5);
        if (Math.hypot(dx, dz) < 1e-4) return lastPlayedYaw; // 正好站在桌心：保持上一次朝向，避免突然翻转
        return DDZGeom.facingYaw(dx, dz);
    }

    /** 给玩家回一句提示（可带格式化参数，如"本桌房主是 %s"）。 */
    private void sendFeedback(ServerPlayer player, String key, Object... args) {
        player.sendSystemMessage(Component.translatable(key, args));
    }

    /**
     * 快照按接收者单独构建：myIndex / myHand 只含该玩家自己的手牌；
     * visibleHands 决定世界内能否渲染出各玩家的牌面——按"谁能看谁的牌"的服务端配置逐个判定。
     * 参与者的客户端默认拿不到他人牌面数据，想作弊也无从看起。
     */
    private GameStatePayload buildSnapshot(ServerPlayer viewer, String reason) {
        return buildSnapshot(viewer, reason, closed);
    }

    /**
     * 构建快照。
     *
     * @param forceClosed 覆盖 {@code sessionClosed} 标志：停止观战时用它通知客户端清空状态，
     *                    而不必真的把牌局关掉
     */
    private GameStatePayload buildSnapshot(ServerPlayer viewer, String reason, boolean forceClosed) {
        int myIndex = seatOf(viewer.getUUID());
        List<String> names = new ArrayList<>(DDZEngine.PLAYER_COUNT);
        List<Integer> counts = new ArrayList<>(DDZEngine.PLAYER_COUNT);
        List<Boolean> online = new ArrayList<>(DDZEngine.PLAYER_COUNT);
        for (int i = 0; i < DDZEngine.PLAYER_COUNT; i++) {
            ServerPlayer p = seats[i];
            names.add(p == null ? "" : p.getGameProfile().getName());
            counts.add(p == null ? 0 : engine.getPlayerHand(i).size());
            online.add(p != null);
        }
        List<Integer> lastCards = engine.getLastPlayedCards() == null
            ? List.of()
            : new ArrayList<>(engine.getLastPlayedCards());
        List<Integer> myHand = myIndex < 0
            ? List.of()
            : new ArrayList<>(engine.getPlayerHand(myIndex));
        // 结算阶段牌局已结束：把三家剩余手牌都放进快照供客户端结算界面展示（不涉密）
        boolean settled = engine.getPhase() == DDZGamePhase.SETTLED;
        List<List<Integer>> allHands = new ArrayList<>(DDZEngine.PLAYER_COUNT);
        for (int i = 0; i < DDZEngine.PLAYER_COUNT; i++) {
            allHands.add(settled ? new ArrayList<>(engine.getPlayerHand(i)) : List.of());
        }
        // 底牌在叫分阶段**不下发**：它决定叫几分的判断，而下发是"谁都能读"的——
        // 若照发，改过的客户端在决定叫分前就能看到 3 张底牌（HUD 只在出牌阶段画它，
        // 那只是"画不画"，数据已经在包里）。出牌阶段底牌已归地主、属于公开信息。
        boolean dipaiPublic = engine.getPhase() == DDZGamePhase.PLAYING;
        List<Integer> dipai = dipaiPublic ? new ArrayList<>(engine.getDipai()) : List.of();
        // 世界内各玩家持牌的可见性：自己的牌 HUD 已有（世界内不重复渲染）；
        // 旁观者看牌面走 spectatorsSeeCards，参与者看他人牌面走 participantsSeeFaces（默认关，防作弊）
        List<List<Integer>> visibleHands = new ArrayList<>(DDZEngine.PLAYER_COUNT);
        for (int i = 0; i < DDZEngine.PLAYER_COUNT; i++) {
            boolean maySee = DDZVisibility.canSeeHand(myIndex >= 0, i == myIndex,
                ServerGameConfig.spectatorsSeeCards, ServerGameConfig.participantsSeeFaces);
            visibleHands.add(maySee ? new ArrayList<>(engine.getPlayerHand(i)) : List.of());
        }
        // 每桌配置的视图字段（都按接收者算）：
        //   iAmHost = 我是不是本桌房主（只有房主能改配置）
        //   configOpen = 配置界面现在是不是开在我这儿（服务端持有那把锁，客户端只认它）
        //   tableChipsEnabled = 本桌自己的筹码开关（原始值，不受全局闸门影响）——
        //     闸门关掉时 chipRequired 也是 0，只看那个数字分不清"服务器不让用"还是"这桌自己关了"
        //   tableEntryCount = 本桌门槛的<b>原始</b>值（0 = 自动）：配置界面要拿它回填，
        //     只看实际生效值会把"自动"和"手动"混成一种，一保存就把房主的手动值改掉
        DDZTableBlockEntity table = tableConfig(viewer);
        boolean iAmHost = table != null && table.isHost(viewer.getUUID());
        boolean configOpen = !forceClosed && table != null && table.isConfigLocker(viewer.getUUID());
        boolean tableChips = table == null || table.chipsEnabled();
        int tableEntry = table == null ? 0 : table.entryCount();
        // 每轮倒计时的"还剩多少刻"（-1 = 不限时）。下发剩余值而不是截止刻：客户端不必知道
        // 服务端的 tick 基准，收到就本地续算（见 ClientDDZData#turnRemainingTicks）。
        int turnLeft = turnRemainingTicks(viewer.serverLevel().getServer().getTickCount());
        return new GameStatePayload(
            key,
            myIndex,
            names,
            counts,
            online,
            engine.getPhase(),
            engine.getLandlordIndex(),
            engine.getCurrentPlayerIndex(),
            engine.getBidScore(),
            engine.getLastPlayedBy(),
            lastCards,
            engine.getLastPlayedType(),
            lastPlayedYaw,
            dipai,
            myHand,
            allHands,
            visibleHands,
            engine.getWinnerTeam(),
            forceClosed,
            reason,
            // 本桌配置的筹码物品（原始值，可能配了但本桌/全局把筹码关着）。
            // "这桌到底玩不玩筹码"看的是 chipRequired，不看这个字段非空：
            // 把配置界面里的筹码类型藏着不发，房主一进界面就显示"未选择"，
            // 顺手点个保存就把自己选过的筹码类型抹掉了——静默改配置。
            // 画不画筹码图标之类的展示由客户端按 chipRequired 判断（WorldTableChip / 结算界面）。
            chipItemName(),
            // 复用这两个已有字段承载"底注"与"倍数"，避免为一个显示值再改协议：
            //   底注 = 本桌配置的 stake；倍数 = 2^(炸弹+火箭) × 春天系数
            stake(),
            engine.getMultiplier(),
            requiredChips(),
            iAmHost,
            configOpen,
            tableChips,
            tableEntry,
            turnLeft);
    }
}
