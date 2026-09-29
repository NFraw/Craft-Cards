package com.jokernan.craftycards.client;

import com.jokernan.craftycards.game.DDZCardType;
import com.jokernan.craftycards.game.DDZEngine;
import com.jokernan.craftycards.game.DDZGamePhase;
import com.jokernan.craftycards.network.payload.GameStatePayload;
import com.jokernan.craftycards.network.payload.PlayerActionPayload;
import com.jokernan.craftycards.network.payload.TableKey;
import com.jokernan.craftycards.platform.Network;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 客户端斗地主状态单例：缓存服务端广播的最新快照与本地选中牌，
 * 提供右键牌桌（加入/出牌/过牌）与选牌操作，发送 C2S 包。
 * 渲染由 {@link DDZGameHud} 完成，不再打开任何 Screen。
 */
public class ClientDDZData {
    private static final ClientDDZData INSTANCE = new ClientDDZData();

    private GameStatePayload snapshot;
    private final List<Integer> selectedCards = new ArrayList<>();
    private String message = "";
    private long messageUntil = 0;
    /** 滚轮选牌：当前焦点在手牌中的位置（PLAYING 阶段）。 */
    private int scrollFocus = 0;
    /** 滚轮叫分：0=不叫 1=1分 2=2分 3=3分（BIDDING 阶段）。 */
    private int bidChoice = 0;
    /** 结算音效是否已播放（避免重复播放） */
    private boolean settlementSoundPlayed = false;
    /**
     * 每轮倒计时的本地基准：快照到达那一刻的剩余刻数 + 本地时刻。
     *
     * <p>服务端只在状态变化与旁观补发时下发快照，而倒计时是每帧都在走的：所以拿"下发那一刻的
     * 剩余量"当起点，本地按 50ms/刻 续算，下一份快照到了再对齐（不会越走越偏）。</p>
     */
    private int turnCountdownBaseTicks = -1;
    private long turnCountdownSetAtMs;

    public static ClientDDZData getInstance() {
        return INSTANCE;
    }

    public GameStatePayload getSnapshot() {
        return snapshot;
    }

    public List<Integer> getSelectedCards() {
        return selectedCards;
    }

    public void apply(GameStatePayload payload) {
        // 收到"结束类"快照（解散 / 旁观别人的结算）时提示一句就清空状态：
        // 这类快照之后服务端不会再下发，若把状态留着就会永久占屏（全屏结算遮罩曾因此卡死旁观者）。
        // 决策规则见 ClientSnapshotPolicy（纯函数，有单测）。
        ClientSnapshotPolicy.Action action = ClientSnapshotPolicy.decide(
            payload.sessionClosed(), payload.phase() == DDZGamePhase.SETTLED, payload.myIndex() >= 0);
        if (action == ClientSnapshotPolicy.Action.NOTIFY_AND_CLEAR) {
            String reason = payload.feedback().isEmpty() ? "牌局已解散" : payload.feedback();
            reset();
            notify(reason);
            return;
        }
        if (action == ClientSnapshotPolicy.Action.NOTIFY_RESULT_AND_CLEAR) {
            String result = payload.winnerTeam() >= 0
                ? "地主 " + playerNameOf(payload, payload.winnerTeam()) + " 获胜"
                : "农民获胜";
            reset();
            notify(result);
            return;
        }

        // 操作音效：靠"快照变化"驱动（服务端只发状态，不发音频指令）。
        // 必须在覆盖 snapshot 之前比较，且首次快照不发声——否则一入局就响一串
        if (snapshot != null) playTransitionSounds(snapshot, payload);

        this.snapshot = payload;
        // 倒计时基准：以这份快照里的剩余量为起点，之后本地续算（见 turnRemainingTicks）
        this.turnCountdownBaseTicks = payload.turnRemainingTicks();
        this.turnCountdownSetAtMs = System.currentTimeMillis();
        // 服务端把配置界面的锁给了我（configOpen）就打开界面。关不关由界面自己盯着快照决定
        // （见 TableConfigScreen#tick）：锁被收回时服务端会补一份 configOpen=false 的快照，
        // 这里就不必再写一套"主动关闭"的对称逻辑。
        if (payload.configOpen() && payload.iAmHost()
            && !(Minecraft.getInstance().screen instanceof TableConfigScreen)) {
            Minecraft.getInstance().setScreen(new TableConfigScreen());
        }
        // 服务端已剔除打出的牌：清理本地选中里已不在手牌的卡
        selectedCards.removeIf(id -> !payload.myHand().contains(id));
        if (!payload.myHand().isEmpty()) {
            scrollFocus = Math.min(scrollFocus, payload.myHand().size() - 1);
        } else {
            scrollFocus = 0;
        }
        // 进入结算阶段时重置音效播放标志
        if (payload.phase() == DDZGamePhase.SETTLED) {
            settlementSoundPlayed = false;
        }
    }

    /**
     * 右键牌桌（方块交互客户端分支）：
     * Shift+右键 = 参局者离开牌局 / 未入局者打开本桌配置界面（不是房主时服务端把它当作观战开关）；
     * 未入局 → JOIN；叫分阶段轮到我 → 按滚轮所选发 BID；
     * 出牌阶段轮到我 → 有选中牌出牌（本地牌型预检），无选中牌过牌。
     *
     * <p>筹码类型不再由玩家手持物品声明（那条路已下线）：它和底注、门槛一起属于<b>本桌</b>配置，
     * 由房主在配置界面里定，存在牌桌方块实体上。</p>
     */
    public void onTableRightClicked(TableKey key, boolean sneaking) {
        GameStatePayload snap = snapshot;
        if (sneaking) {
            // 参局者 = 离开牌局；其余人交给服务端判：本桌还没房主（或房主离线）→ 我成为房主并开配置界面，
            // 已经有在线房主 → 退回原用途"观战开关"。客户端不猜自己是哪种身份：
            // 快照里的 iAmHost 是"我已是房主"，分不出"还没有房主"，猜错就会把观战入口弄丢。
            boolean participant = snap != null && snap.myIndex() >= 0 && !snap.sessionClosed();
            send(new PlayerActionPayload(key,
                participant ? PlayerActionPayload.Action.LEAVE : PlayerActionPayload.Action.OPEN_TABLE_CONFIG,
                0, List.of()));
            return;
        }
        if (snap == null || snap.myIndex() < 0 || snap.sessionClosed()) {
            send(new PlayerActionPayload(key, PlayerActionPayload.Action.JOIN, 0, List.of()));
            return;
        }
        if (snap.phase() == DDZGamePhase.BIDDING && snap.currentPlayerIndex() == snap.myIndex()) {
            // 叫分必须高于当前叫分：本地先挡一道，否则滚轮停在"1分"时右键只会收到
            // 服务端的"无效叫分"，玩家以为界面坏了（服务端仍会校验，这里只是提前提示）
            if (bidChoice > 0 && bidChoice <= snap.bidScore()) {
                showMessage("叫分必须高于当前叫分 " + snap.bidScore() + "，或选「不叫」");
                return;
            }
            send(new PlayerActionPayload(key, PlayerActionPayload.Action.BID, bidChoice, List.of()));
            return;
        }
        if (snap.phase() != DDZGamePhase.PLAYING || snap.currentPlayerIndex() != snap.myIndex()) {
            return; // 未轮到时不响应右键
        }
        if (selectedCards.isEmpty()) {
            // 与引擎 pass() 一致：开局地主 / 两连过后自己 = 新一轮牌权，必须出牌，不能过
            if (snap.lastPlayedBy() < 0 || snap.lastPlayedBy() == snap.myIndex()) {
                showMessage("你是新一轮牌权，请先选牌再出牌");
                return;
            }
            send(new PlayerActionPayload(key, PlayerActionPayload.Action.PASS, 0, List.of()));
            return;
        }
        if (DDZEngine.analyzeCardType(selectedCards) == DDZCardType.INVALID) {
            showMessage("无效的牌型");
            return;
        }
        send(new PlayerActionPayload(key, PlayerActionPayload.Action.PLAY, 0, new ArrayList<>(selectedCards)));
        selectedCards.clear();
    }

    public int getScrollFocus() {
        return scrollFocus;
    }

    public int getBidChoice() {
        return bidChoice;
    }

    /**
     * 当前轮次还剩多少刻（HUD 每帧取用，所以是本地续算的实时值）；<b>-1 = 本服务器不限时</b>。
     *
     * <p>基准来自最近一份快照（{@code turnRemainingTicks}），每过 50ms 减一刻；下一份快照到达时
     * 重新对齐，所以不会累积漂移。倒计时走完（本地算出 0）之后服务端最多再等一个清扫节拍
     * （{@code DDZTableManager.TIMEOUT_CHECK_INTERVAL}）才判负，那段时间显示 0 是对的。</p>
     */
    public int turnRemainingTicks() {
        if (turnCountdownBaseTicks < 0) return -1;
        long elapsedMs = System.currentTimeMillis() - turnCountdownSetAtMs;
        return (int) Math.max(0, turnCountdownBaseTicks - elapsedMs / 50);
    }

    /** 滚轮滚动：方向与滚轮相反（向上滚左移、向下滚右移）。
     *  出牌阶段无论是否轮到自己都移动手牌焦点（非回合也能预选）；
     *  叫分阶段仅在轮到自己时循环切换叫分选项。 */
    public void scroll(double delta) {
        GameStatePayload snap = snapshot;
        if (snap == null || snap.myIndex() < 0 || snap.sessionClosed()) return;
        int dir = delta > 0 ? -1 : 1;
        if (snap.phase() == DDZGamePhase.BIDDING) {
            if (snap.currentPlayerIndex() == snap.myIndex()) {
                bidChoice = Math.floorMod(bidChoice + dir, 4);
            }
        } else if (snap.phase() == DDZGamePhase.PLAYING) {
            int n = snap.myHand().size();
            if (n > 0) scrollFocus = Math.floorMod(scrollFocus + dir, n);
        }
    }

    /** 左键确认：把滚轮焦点牌加入/移出选中集合（出牌阶段，非回合也能预选）。 */
    public void toggleFocusCard() {
        GameStatePayload snap = snapshot;
        if (snap == null || snap.myIndex() < 0 || snap.sessionClosed()) return;
        if (snap.phase() != DDZGamePhase.PLAYING) return;
        List<Integer> hand = snap.myHand();
        if (hand.isEmpty()) return;
        toggleCard(hand.get(Math.min(scrollFocus, hand.size() - 1)));
    }

    public void toggleCard(int cardId) {
        if (selectedCards.contains(cardId)) {
            selectedCards.remove(Integer.valueOf(cardId));
        } else {
            selectedCards.add(cardId);
        }
    }

    public void clearSelection() {
        selectedCards.clear();
    }

    /**
     * 比较前后两份快照，为发生变化的事件配音效。
     *
     * <p>只按"状态确实变了"发声，不做同状态重复播放：快照还会因旁观刷新、玩家进出等
     * 原因重复下发，若不做差异比较会连播。</p>
     */
    private static void playTransitionSounds(GameStatePayload previous, GameStatePayload now) {
        // 开局发牌：等待 → 叫分
        if (previous.phase() == DDZGamePhase.WAITING && now.phase() == DDZGamePhase.BIDDING) {
            ClientSounds.deal();
        }
        // 叫分：分数被抬高（含地主定下来的那次）
        if (now.phase() == DDZGamePhase.BIDDING && now.bidScore() > previous.bidScore()) {
            ClientSounds.bidConfirmed();
        }
        // 出牌：上家出的牌变了（张数或出牌人变化即视为一次新的出牌）
        boolean newPlay = now.lastPlayedBy() != previous.lastPlayedBy()
            || now.lastPlayedCards().size() != previous.lastPlayedCards().size()
            || !now.lastPlayedCards().equals(previous.lastPlayedCards());
        boolean playedNow = newPlay && !now.lastPlayedCards().isEmpty();
        if (playedNow) {
            boolean big = now.lastPlayedType() == DDZCardType.BOMB
                || now.lastPlayedType() == DDZCardType.ROCKET;
            ClientSounds.cardPlayed(now.lastPlayedType(), now.lastPlayedCards(), big);
        } else if (previous.phase() == DDZGamePhase.PLAYING && now.phase() == DDZGamePhase.PLAYING
            && now.currentPlayerIndex() != previous.currentPlayerIndex()) {
            // 轮次推进了但没有新出牌 = 有人过牌。
            // 注意"两连过后牌权回到原出牌人"那一步会把 lastPlayedCards 清空、lastPlayedBy 置 -1，
            // 此时 newPlay 为真但牌面为空，故用 playedNow 判断才不会把它误当成出牌。
            ClientSounds.passed();
        }
    }

    /**
     * 用动作栏提示一句（原版会显示约 3 秒后自动淡出）。
     * <p>不走 HUD 的消息通道：调用时快照已清空，而 HUD 没有快照就什么都不画，
     * 消息会跟着一起消失。</p>
     */
    private void notify(String text) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) mc.player.displayClientMessage(Component.literal(text), true);
    }

    /** 快照里的玩家名（空则用"玩家N"占位），与 HUD 的显示保持一致。 */
    private static String playerNameOf(GameStatePayload snap, int index) {
        if (index < 0 || index >= snap.playerNames().size()) return "?";
        String name = snap.playerNames().get(index);
        return name.isEmpty() ? "玩家" + (index + 1) : name;
    }

    /** 显示一条 3 秒后消失的提示（HUD 渲染）。 */
    public void showMessage(String msg) {
        this.message = msg;
        this.messageUntil = System.currentTimeMillis() + 3000;
    }

    public String getMessage() {
        return System.currentTimeMillis() > messageUntil ? "" : message;
    }

    public boolean isSettlementSoundPlayed() {
        return settlementSoundPlayed;
    }

    public void setSettlementSoundPlayed(boolean played) {
        this.settlementSoundPlayed = played;
    }

    public void send(PlayerActionPayload payload) {
        // 平台接缝：具体用哪个网络的管道由加载器决定（见 platform/Network）
        Network.sendToServer(payload);
    }

    public void reset() {
        this.snapshot = null;
        selectedCards.clear();
        message = "";
        messageUntil = 0;
        scrollFocus = 0;
        bidChoice = 0;
        settlementSoundPlayed = false;
        turnCountdownBaseTicks = -1;
        turnCountdownSetAtMs = 0;
    }

    /** 获取当前押注物品的显示名称。 */
    public String getBetItemName() {
        if (snapshot == null || snapshot.betItem().isEmpty()) return "无";
        return snapshot.betItem();
    }

    /** 获取当前押注每份数量。 */
    public int getBetCount() {
        return snapshot == null ? 0 : snapshot.betCount();
    }

    /** 获取当前押注倍率。 */
    public int getBetMulti() {
        return snapshot == null ? 1 : snapshot.betMulti();
    }
}
