package com.jokernan.craftycards.client;

import com.jokernan.craftycards.block.BlockDDZTable;
import com.jokernan.craftycards.game.DDZCardType;
import com.jokernan.craftycards.game.DDZEngine;
import com.jokernan.craftycards.game.DDZGamePhase;
import com.jokernan.craftycards.init.InitItems;
import com.jokernan.craftycards.network.payload.GameStatePayload;
import com.jokernan.craftycards.CCReference;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * 斗地主 HUD（网络驱动）：不是全屏 Screen，而是叠加在主世界上的信息层，
 * 玩家可以看到牌桌周围的世界。状态全部来自 {@link ClientDDZData} 缓存的服务端快照。
 *
 * <p>交互方式（全部在座椅上操作）：
 * <ul>
 *   <li>右键牌桌方块 = 加入（未入座时），由 {@code BlockDDZTable.useWithoutItem} 触发</li>
 *   <li>叫地主阶段：滚轮在【不叫/1分/2分/3分】间循环（{@link #handleScroll}），当前选项金色高亮，右键牌桌确认</li>
 *   <li>出牌阶段：滚轮移动手牌焦点（黄色边框），左键确认把焦点牌加入/移出选中集合（{@link #handleMouseClick} 拦截事件防破坏方块）</li>
 *   <li>右键牌桌方块 = 出牌（有选中牌）/ 过牌（无选中牌），也是由方块交互触发</li>
 * </ul>
 */
public class DDZGameHud {
    // 手牌渲染参数全部来自 RenderConfig（hud* 字段），可用 /craftycards reload 热更新。
    // 卡牌 item 模型（card_base.json）整体 x 范围 2.9~13.1（宽 10.2，中心 8.0），z 长 16。
    // FaceBakery 烘焙把 0..16 模型缩到 0..1 空间，故卡牌比例需乘 16 补偿（见 RenderConfig.hudCardScale）；
    // 渲染高度 = 烘焙 z 长 1.0 × scale。

    /** HUD 元素距屏幕左右边缘的最小边距（底牌、上轮出牌共用）。 */
    private static final int HUD_EDGE_MARGIN = 10;
    /** 上轮出牌：标签与牌行之间的间隙（像素）。 */
    private static final int LABEL_GAP = 6;

    private static final DDZGameHud INSTANCE = new DDZGameHud();

    /**
     * 玩家是否握着斗地主扑克（主手）。握着才展开自己的手牌、才接管滚轮与左右键——
     * 这样遇到危险可以直接切武器，切走只是收起牌面，牌局照旧。
     */
    private static boolean holdingCards() {
        Minecraft mc = Minecraft.getInstance();
        return mc.player != null && mc.player.getMainHandItem().is(InitItems.DDZ_CARD.get());
    }

    /** 礼花用随机源：固定种子逐帧重置，保证图案静止不闪烁；复用实例避免每帧新建。 */
    private final Random confettiRandom = new Random();

    private DDZGameHud() {}

    /**
     * 供加载器的客户端 tick 调用的一次性诊断：测卡牌渲染**缓存路径**（HUD 手牌真正走的那条）。
     * 包一层只是为了让包外的加载器代码够得着包内的 {@link CardStacks}。
     */
    public static void probeCachedCardsOnce() {
        CardStacks.probeCachedOnce();
    }

    /** 上一次打过诊断的手牌（手牌一变就打一条，用于确认客户端拿到的 ID 是否各不相同）。 */
    private static List<Integer> lastLoggedHand;

    private void logHandOnce(List<Integer> hand) {
        if (hand.equals(lastLoggedHand)) return;
        lastLoggedHand = new ArrayList<>(hand);
        int distinct = new java.util.HashSet<>(hand).size();
        CCReference.LOG.info("HUD 手牌诊断: {} 张，去重后 {} 种，前 10 个 ID={}",
            hand.size(), distinct, hand.subList(0, Math.min(10, hand.size())));
    }

    public static DDZGameHud getInstance() {
        return INSTANCE;
    }

    /** RegisterGuiLayersEvent 注册的渲染层。 */
    public void render(GuiGraphics guiGraphics, DeltaTracker deltaTracker) {
        HudHandMetrics.Viewport viewport = viewport();
        PoseStack pose = guiGraphics.pose();
        pose.pushPose();
        pose.scale(viewport.scale(), viewport.scale(), 1F);
        try {
            renderAtViewport(guiGraphics, deltaTracker);
        } finally {
            pose.popPose();
        }
    }

    private HudHandMetrics.Viewport viewport() {
        var window = Minecraft.getInstance().getWindow();
        return HudHandMetrics.viewport(window.getGuiScaledWidth(), window.getGuiScaledHeight());
    }

    private void renderAtViewport(GuiGraphics guiGraphics, DeltaTracker deltaTracker) {
        GameStatePayload snap = ClientDDZData.getInstance().getSnapshot();
        Minecraft mc = Minecraft.getInstance();
        if (snap == null || mc.player == null || mc.level == null) return;

        int width = viewport().width();
        int height = viewport().height();

        guiGraphics.drawCenteredString(mc.font, "斗地主", width / 2, 4, 0xFFFFFFFF);

        // 结算庆祝界面：全屏覆盖，提前 return 不画手牌/操作提示
        if (snap.phase() == DDZGamePhase.SETTLED) {
            renderSettlement(guiGraphics, snap);
            return;
        }

        // 阶段 + 回合合并一行
        String phaseText = switch (snap.phase()) {
            case WAITING -> "等待中";
            case BIDDING -> "叫地主";
            case PLAYING -> "出牌中";
            case SETTLED -> "结算";
        };
        if (snap.phase() == DDZGamePhase.PLAYING || snap.phase() == DDZGamePhase.BIDDING) {
            String turnText = snap.currentPlayerIndex() == snap.myIndex()
                ? (snap.phase() == DDZGamePhase.PLAYING ? "轮到你出牌" : "轮到你叫分")
                : "等待: " + playerName(snap, snap.currentPlayerIndex());
            phaseText = phaseText + " | " + turnText;
        }
        // 每轮倒计时接在同一行后面（单独上色：越紧越红）。倒计时是本地续算的实时值，
        // 所以这一行每帧都会变——不限时（-1）时 label 返回空串，整行与从前完全一样。
        int turnLeft = ClientDDZData.getInstance().turnRemainingTicks();   // 取一次，别让两段读到不同的刻
        String countdown = TurnCountdown.label(turnLeft);
        if (countdown.isEmpty()) {
            guiGraphics.drawCenteredString(mc.font, phaseText, width / 2, 16, 0xFFAAAAAA);
        } else {
            // 两段分开画：阶段文字保持灰、倒计时按紧迫程度变色（居中的总宽度要把两段一起算）
            int textX = (width - mc.font.width(phaseText) - mc.font.width(countdown)) / 2;
            guiGraphics.drawString(mc.font, phaseText, textX, 16, 0xFFAAAAAA);
            guiGraphics.drawString(mc.font, countdown, textX + mc.font.width(phaseText), 16,
                TurnCountdown.color(turnLeft));
        }

        if (snap.phase() == DDZGamePhase.WAITING) {
            guiGraphics.drawCenteredString(mc.font,
                "等待玩家加入 (" + countOnline(snap) + "/3)",
                width / 2, 30, 0xFFFFAA00);
            // chipRequired = 0 表示本桌不玩筹码（全局闸门关掉，或这张桌自己关了）：
            // 直接入局即可，不要再提示"先配筹码"，否则玩家会去找一个根本不存在的步骤
            if (snap.chipRequired() <= 0) {
                guiGraphics.drawCenteredString(mc.font, "本桌不需要筹码 · 右键牌桌入局",
                    width / 2, 46, 0xFF55FF55);
                guiGraphics.drawCenteredString(mc.font, "开局后即可叫分",
                    width / 2, 58, 0xFFAAAAAA);
                return;
            }
            // 本桌筹码由房主在配置界面里定（存在这张桌子上）：没配时明确告诉玩家找谁配，
            // 否则右键入局只会"没反应"
            if (snap.betItem().isEmpty()) {
                guiGraphics.drawCenteredString(mc.font, "本桌筹码还没配",
                    width / 2, 46, 0xFFFF5555);
                guiGraphics.drawCenteredString(mc.font,
                    "房主 Shift+右键牌桌 → 在配置界面里选筹码物品",
                    width / 2, 58, 0xFFAAAAAA);
            } else {
                guiGraphics.drawCenteredString(mc.font,
                    "筹码: " + chipName(snap) + "（入局需备 " + snap.chipRequired() + " 个）",
                    width / 2, 46, 0xFF55FF55);
                boolean host = snap.iAmHost();
                guiGraphics.drawCenteredString(mc.font,
                    host ? "Shift+右键牌桌可改本桌配置（筹码/底注/门槛）· 右键牌桌入局"
                         : "右键牌桌入局 · 房主可 Shift+右键改本桌配置",
                    width / 2, 58, 0xFFAAAAAA);
            }
            return;
        }

        if (snap.landlordIndex() >= 0) {
            guiGraphics.drawCenteredString(mc.font, "地主: " + playerName(snap, snap.landlordIndex()), width / 2, 30, 0xFFFFD700);
        }

        // 底牌（开局后可见）：左上角左对齐
        if (snap.phase() == DDZGamePhase.PLAYING && !snap.dipai().isEmpty()) {
            guiGraphics.drawString(mc.font, "底牌", 10, 96, 0xFF55AAFF);
            renderCardsRow(guiGraphics, snap.dipai(), 126, 10);
        }

        if (snap.phase() == DDZGamePhase.BIDDING && snap.currentPlayerIndex() == snap.myIndex()) {
            renderBidOptions(guiGraphics, width, height, snap.bidScore());
        } else if (holdingCards() && snap.phase() == DDZGamePhase.PLAYING
            && snap.currentPlayerIndex() == snap.myIndex()) {
            // 操作提示：lastPlayedBy<0（开局地主）或 lastPlayedBy==自己（两连过后）＝新一轮牌权，必须出牌不能过
            boolean leading = snap.lastPlayedBy() < 0 || snap.lastPlayedBy() == snap.myIndex();
            List<Integer> selected = ClientDDZData.getInstance().getSelectedCards();
            String hint;
            if (selected.isEmpty()) {
                hint = leading
                    ? "滚轮选牌 | 左键确认 | 右键牌桌 = 出牌（新一轮牌权，不能过）"
                    : "滚轮选牌 | 左键确认 | 右键牌桌 = 过牌";
            } else {
                List<Integer> sorted = new ArrayList<>(selected);
                Collections.sort(sorted);
                // 手写拼接替代 stream+collect：逐帧执行，避免 stream 管线与中间字符串的分配
                StringBuilder ranks = new StringBuilder();
                for (int cardId : sorted) {
                    if (ranks.length() > 0) ranks.append(' ');
                    ranks.append(rankLabel(cardId));
                }
                hint = "已选 [" + ranks + "] | 右键牌桌 = 出牌";
            }
            guiGraphics.drawCenteredString(mc.font, hint, width / 2, height - 188, 0xFF55FF55);
        }

        // 本局提示（如"无效的牌型"）
        String msg = ClientDDZData.getInstance().getMessage();
        if (!msg.isEmpty() && snap.phase() != DDZGamePhase.BIDDING) {
            guiGraphics.drawCenteredString(mc.font, msg, width / 2, height - 172, 0xFFFF5555);
        }

        // 手牌 / 焦点框 / 上轮出牌块都只在握着斗地主扑克时展开（世界内的牌不受影响）
        if (holdingCards()) {
            renderHand(guiGraphics, snap);
            renderFocusFrame(guiGraphics, snap);
            // 上轮出牌块：默认关闭（改看世界内桌面上的牌），可用 visual.json 的 hudPlayedShow 打开
            if (RenderConfig.hudPlayedShow) renderLastPlayed(guiGraphics, snap);
        } else if (snap.phase() == DDZGamePhase.PLAYING && snap.currentPlayerIndex() == snap.myIndex()) {
            // 轮到你了却没握牌：给出明确提示，否则玩家会以为牌局卡住了
            guiGraphics.drawCenteredString(mc.font, "握上斗地主扑克才能出自己的牌",
                width / 2, height - 188, 0xFFFFAA00);
        }
    }

    /**
     * 上轮出牌（HUD）：与左上角的底牌行<b>同一套渲染</b>——同一个 helper、同样的牌宽与间距，
     * 等距平铺、不压缩不叠压；标签也照底牌那样放在牌行正上方（不再压半透明底衬）。
     *
     * <p>默认关闭，由 {@link RenderConfig#hudPlayedShow} 控制（世界内桌面上另有一份常开的平铺）。
     * 牌行的落点仍由 {@link HudPlayedLayout} 解算（纯算术，有单测保证不出屏、不压手牌）。</p>
     */
    private void renderLastPlayed(GuiGraphics guiGraphics, GameStatePayload snap) {
        List<Integer> cards = snap.lastPlayedCards();
        if (cards.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();

        // 牌行几何 = 底牌那套（cardW/gap 都用 hudSmall*）；labelWidth 传 0：标签在牌行正上方，
        // 不占横向空间，牌行自己贴着手牌右上角外侧。
        HudPlayedLayout layout = HudPlayedLayout.solve(
            viewport().width(), handEndX(), handBaseY(), cards.size(), 0,
            new HudPlayedLayout.Config(
                RenderConfig.hudSmallCardW, RenderConfig.hudSmallCardGap, RenderConfig.hudPlayedMinGap,
                RenderConfig.hudPlayedOffsetX, RenderConfig.hudPlayedOffsetY, HUD_EDGE_MARGIN, LABEL_GAP));

        // 标签：底牌那款（牌行正上方的小字）；炸弹/火箭用金色突出
        boolean big = snap.lastPlayedType() == DDZCardType.BOMB || snap.lastPlayedType() == DDZCardType.ROCKET;
        String label = playerName(snap, snap.lastPlayedBy()) + " 出牌 · " + snap.lastPlayedType().getDisplayName();
        int labelW = mc.font.width(label);
        int screenW = viewport().width();
        int labelX = Math.max(HUD_EDGE_MARGIN, Math.min(layout.rowLeft(), screenW - HUD_EDGE_MARGIN - labelW));
        int labelY = layout.rowBottom() - layout.rowHeight() - LABEL_GAP - mc.font.lineHeight;
        guiGraphics.drawString(mc.font, label, labelX, labelY, big ? 0xFFFFD700 : 0xFFAAAAAA);

        // 牌面：与底牌行完全同一个调用（内部就是 hudSmallCardW / hudSmallCardGap）
        renderCardsRow(guiGraphics, cards, layout.rowCenterY(), layout.rowLeft());
    }

    /** 手牌区右边缘 x（与 {@link #handLayout()} 同一份布局，避免两处各算一遍）。 */
    private int handEndX() {
        HudHandMetrics.Layout l = handLayout();
        return l.startX() + l.totalWidth();
    }

    /** 结算庆祝界面：全屏半透明覆盖 + 获胜方 + 三家剩余手牌 + 点击关闭提示。
     *  游戏结束时服务端已清退玩家（下马清座），快照 {@code allHands} 含三家完整剩余手牌。 */
    private void renderSettlement(GuiGraphics guiGraphics, GameStatePayload snap) {
        Minecraft mc = Minecraft.getInstance();
        int width = viewport().width();
        int height = viewport().height();

        guiGraphics.fill(0, 0, width, height, 0xA0000000);

        // 判断当前玩家是否获胜
        boolean viewerWins;
        if (snap.winnerTeam() >= 0) {
            // 地主获胜 - 只有地主玩家获胜
            viewerWins = (snap.myIndex() == snap.landlordIndex());
        } else {
            // 农民获胜 - 非地主玩家获胜
            viewerWins = (snap.myIndex() != snap.landlordIndex());
        }

        // 播放结算音效（仅播放一次）
        if (!ClientDDZData.getInstance().isSettlementSoundPlayed()) {
            playSettlementSound(mc, viewerWins);
            ClientDDZData.getInstance().setSettlementSoundPlayed(true);
        }

        // 礼花粒子效果（玩家获胜时播放）
        if (viewerWins) {
            renderConfetti(guiGraphics, width, height);
        }

        int winner = snap.winnerTeam();
        String result = winner >= 0
            ? "地主 " + playerName(snap, winner) + " 获胜！"
            : "农民获胜！";

        // 竖排位置全部交给 HudSettlementLayout 解算（纯算术、有单测），
        // 这里只负责往给定位置画内容——原先手调的魔法偏移会把三家顺序打乱并互相压住
        // 只有真的玩筹码（chipRequired > 0）才画这一行：桌上可能还留着上次选的筹码类型，
        // 但这一局一件物品都没转移，不该显示"筹码: 钻石 · 底注 …"
        boolean hasBet = snap.chipRequired() > 0 && !snap.betItem().isEmpty();
        HudSettlementLayout layout = HudSettlementLayout.solve(
            height, mc.font.lineHeight, RenderConfig.hudSmallCardH(), hasBet ? 2 : 0);

        guiGraphics.drawCenteredString(mc.font, result, width / 2, layout.resultY, 0xFFFFD700);

        if (hasBet) {
            // 底分 × 倍数 × 底注：与结算实际用的公式同一套（见 DDZSession.scorePerFarmer）
            int perFarmer = snap.bidScore() * snap.betMulti() * snap.betCount();
            String betInfo = "筹码: " + chipName(snap) + " · 底注 " + snap.betCount()
                + " · 底分 " + snap.bidScore() + " × 倍数 " + snap.betMulti();
            guiGraphics.drawCenteredString(mc.font, betInfo, width / 2, layout.betY, 0xFF55FF55);
            String winInfo = winner >= 0
                ? "每位农民输 " + perFarmer + " 个，地主赢 " + (perFarmer * 2) + " 个"
                : "每位农民赢 " + perFarmer + " 个，地主输 " + (perFarmer * 2) + " 个";
            guiGraphics.drawCenteredString(mc.font, winInfo, width / 2, layout.betWinY, 0xFFFFD700);
        }

        List<List<Integer>> allHands = snap.allHands();
        for (int i = 0; i < 3; i++) {
            List<Integer> hand = i < allHands.size() ? allHands.get(i) : List.of();
            // 标出地主，并把自己那行标成浅蓝，方便一眼找到自己
            String label = playerName(snap, i) + (i == snap.landlordIndex() ? "（地主）" : "")
                + " 剩余 " + hand.size() + " 张";
            int color = i == snap.myIndex() ? 0xFF7FD4FF : 0xFFFFFFFF;
            guiGraphics.drawCenteredString(mc.font, label, width / 2, layout.nameY[i], color);
            if (!hand.isEmpty()) {
                // 牌多时压缩间距（最小 14）保证整排不超屏，牌少时用默认间距
                int gap = hand.size() > 1
                    ? Math.max(14, Math.min(RenderConfig.hudSmallCardGap,
                        (width - 20 - RenderConfig.hudSmallCardW) / (hand.size() - 1)))
                    : RenderConfig.hudSmallCardGap;
                int totalW = rowWidth(hand.size(), gap);
                renderCardsRow(guiGraphics, hand, layout.cardsCenterY[i], width / 2 - totalW / 2, gap);
            }
        }

        guiGraphics.drawCenteredString(mc.font, "点击任意处关闭", width / 2, layout.closeY, 0xFF55FF55);
    }

    /** 礼花粒子效果：在结算界面显示彩色粒子动画 */
    private void renderConfetti(GuiGraphics guiGraphics, int width, int height) {
        // 复用随机源并重置种子：图案与固定种子逐帧重算完全一致（静止不闪烁），但不产生分配
        Random random = confettiRandom;
        random.setSeed(12345);
        int particleCount = 50;
        int[] colors = {0xFFFF0000, 0xFF00FF00, 0xFF0000FF, 0xFFFFFF00, 0xFFFF00FF, 0xFF00FFFF};

        for (int i = 0; i < particleCount; i++) {
            int x = random.nextInt(width);
            int y = random.nextInt(height);
            int size = 2 + random.nextInt(4);
            int color = colors[random.nextInt(colors.length)];
            guiGraphics.fill(x, y, x + size, y + size, color);
        }
    }

    /** 播放结算音效（胜利/失败）。映射到原版音效，理由见 {@link ClientSounds}。 */
    private void playSettlementSound(Minecraft mc, boolean isWin) {
        if (mc.level == null || mc.player == null) return;
        ClientSounds.settlement(isWin);
    }

    /**
     * 叫分阶段：显示 4 个滚轮选项，当前选择高亮金色，附确认提示。
     *
     * @param currentBid 当前最高叫分——不高于它的选项会被服务端拒绝，故置灰
     *                   （否则玩家选了"1分"却只收到"无效叫分"，会以为界面坏了）
     */
    private void renderBidOptions(GuiGraphics guiGraphics, int width, int height, int currentBid) {
        Minecraft mc = Minecraft.getInstance();
        int bid = ClientDDZData.getInstance().getBidChoice();
        String[] labels = {"不叫", "1分", "2分", "3分"};
        int itemW = 40;
        int totalW = itemW * 4;
        int startX = width / 2 - totalW / 2;
        int y = height - 165;
        for (int i = 0; i < 4; i++) {
            int x = startX + i * itemW;
            boolean allowed = i == 0 || i > currentBid;
            int color = !allowed ? 0xFF666666
                : i == bid ? 0xFFFFD700 : 0xFFFFFFFF;
            if (i == bid) {
                guiGraphics.fill(x - 2, y - 2, x + itemW - 2, y + 10, 0x66000000);
            }
            guiGraphics.drawCenteredString(mc.font, labels[i], x + itemW / 2 - 2, y, color);
        }
        String hint = currentBid > 0
            ? "滚轮选择 | 右键牌桌确认 · 当前最高叫分 " + currentBid
            : "滚轮选择 | 右键牌桌确认";
        guiGraphics.drawCenteredString(mc.font, hint, width / 2, y + 12, 0xFF55FF55);
    }

    /**
     * 出牌阶段：给滚轮焦点牌画黄色边框，随该张牌的扇形倾角一起旋转（不是屏幕轴对齐的直框）。
     * 非回合也能预选，故不限定当前回合。
     *
     * <p>做法是把 PoseStack 摆到与 {@link #renderCardStack} 完全相同的牌底支点、再转同样的 fan 角
     * （{@code GuiGraphics.fill} 用的是当前 pose 的矩阵，所以能跟着转），然后在牌的局部矩形
     * {@code [-w/2, w/2] × [-h, 0]} 上描四条边——与牌贴合，且两处共用 {@link #handSlot} 不会错位。
     */
    private void renderFocusFrame(GuiGraphics guiGraphics, GameStatePayload snap) {
        if (snap.phase() != DDZGamePhase.PLAYING) return;
        List<Integer> hand = snap.myHand();
        int focus = ClientDDZData.getInstance().getScrollFocus();
        if (hand.isEmpty() || focus < 0 || focus >= hand.size()) return;
        boolean sel = ClientDDZData.getInstance().getSelectedCards().contains(hand.get(focus));
        HandSlot slot = handSlot(focus, hand.size(), sel);

        int halfW = RenderConfig.hudCardW / 2;
        int h = RenderConfig.hudCardH();
        int color = 0xFFFFD700;

        PoseStack pose = guiGraphics.pose();
        pose.pushPose();
        pose.translate(slot.bx(), slot.by(), HudHandMetrics.focusDepth(focus));
        pose.mulPose(Axis.ZP.rotationDegrees(slot.fan()));
        guiGraphics.fill(-halfW - 1, -h - 1, halfW + 1, -h, color); // 上边
        guiGraphics.fill(-halfW - 1, 0, halfW + 1, 1, color);       // 下边（贴牌底）
        guiGraphics.fill(-halfW - 1, -h, -halfW, 0, color);         // 左边
        guiGraphics.fill(halfW, -h, halfW + 1, 0, color);           // 右边
        pose.popPose();
    }

    /**
     * 鼠标按下：左键确认滚轮焦点牌（出牌阶段非回合也能预选）；
     * 右键在出牌阶段且准星不指牌桌时 = 取消全部选中（预选比不过时一键清空，防误破坏方块）；
     * 牌局中均拦截防止误破坏方块。
     *
     * <p><b>返回"要不要拦截这次按键"，而不是直接取消事件</b>：两个加载器的输入事件类型不同
     * （NeoForge 是 {@code InputEvent.MouseButton.Pre}，Fabric 侧来自 {@code MouseHandler.onPress}
     * 的 mixin），common 只回答"拦不拦"，改事件是各自胶水的事——与
     * {@code DDZTableManager.shouldForceBlockUse} 同一套路。</p>
     *
     * @param button 0 = 左键、1 = 右键（与原版 GLFW 一致）
     * @param action 1 = 按下（只处理按下）
     * @return true = 调用方应取消该输入事件
     */
    public boolean handleMouseClick(int button, int action) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.screen != null) return false;
        if (action != 1) return false; // 只处理按下

        GameStatePayload snap = ClientDDZData.getInstance().getSnapshot();
        if (snap == null || snap.myIndex() < 0 || snap.sessionClosed()) return false;

        // 结算界面：任意键点击关闭（reset 后快照为空，HUD 不再渲染）
        if (snap.phase() == DDZGamePhase.SETTLED) {
            ClientDDZData.getInstance().reset();
            return true;
        }

        // 右键：出牌阶段准星不指牌桌（空气/其他方块）= 取消全部选中
        if (button == 1) {
            if (holdingCards() && snap.phase() == DDZGamePhase.PLAYING && !pointingAtTable(mc)) {
                ClientDDZData.getInstance().clearSelection();
                return true;
            }
            return false;
        }
        if (button != 0) return false;

        // 只在握着斗地主扑克时接管左键（防误破坏方块）；没握牌就把操作还给原版
        if (!holdingCards()) return false;
        if (snap.phase() == DDZGamePhase.PLAYING || snap.phase() == DDZGamePhase.BIDDING) {
            if (snap.phase() == DDZGamePhase.PLAYING) {
                ClientDDZData.getInstance().toggleFocusCard();
            }
            return true;
        }
        return false;
    }

    /** 准星是否指向牌桌方块（指向牌桌时右键放行走方块交互出牌/过牌）。 */
    private boolean pointingAtTable(Minecraft mc) {
        if (mc.hitResult instanceof BlockHitResult hit && mc.level != null) {
            return mc.level.getBlockState(hit.getBlockPos()).getBlock() instanceof BlockDDZTable;
        }
        return false;
    }

    /**
     * 滚轮：握着斗地主扑克时，出牌阶段移动手牌焦点 / 叫分阶段切换选项。
     * <p>没握牌就<b>不拦截</b>——滚轮照常切热键栏，玩家才能切回武器或切回扑克。</p>
     *
     * @param scrollDeltaY 原版滚轮增量
     * @return true = 调用方应取消该输入事件
     */
    public boolean handleScroll(double scrollDeltaY) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.screen != null) return false;
        GameStatePayload snap = ClientDDZData.getInstance().getSnapshot();
        if (snap == null || snap.myIndex() < 0 || snap.sessionClosed()) return false;
        if (snap.phase() != DDZGamePhase.BIDDING && snap.phase() != DDZGamePhase.PLAYING) return false;
        if (!holdingCards()) return false;
        ClientDDZData.getInstance().scroll(scrollDeltaY);
        return true;
    }

    /**
     * 手牌第 i 张的屏幕落点。
     *
     * @param bx  牌底中心 x（绕它旋转）
     * @param by  牌底 y（旋转支点）
     * @param fan 扇形倾角（度，绕 Z 轴）
     */
    private record HandSlot(float bx, float by, float fan) {}

    /**
     * 计算手牌第 i 张的落点。{@link #renderHand} 与 {@link #renderFocusFrame} 必须共用这一份，
     * 否则黄色焦点框会和牌错位——这里曾经因为两处各算一遍而出过对齐问题。
     * 每帧最多建 n 个小 record，开销可忽略，换来的是两处不可能算得不一样。
     */
    private HandSlot handSlot(int i, int n, boolean selected) {
        // 扇形角度：中间牌直立，向两侧逐渐倾斜（绕牌底中心旋转）
        float fan = n > 1
            ? (i - (n - 1) / 2f) / ((n - 1) / 2f) * RenderConfig.hudFanAngle
            : 0F;
        float bx = handStartX() + i * handGap() + RenderConfig.hudCardW / 2f;
        // 拱形偏移（正值向下 = 两端下沉），中间牌保持基准高度
        float by = handBaseY() + RenderConfig.hudCardH()
            - (selected ? RenderConfig.hudSelectLift : 0) + archOffset(i, n);
        return new HandSlot(bx, by, fan);
    }

    /** 手牌：用模组卡牌 item 模型渲染，扇形展开 + 拱形（中间高两端低），选中牌上移。 */
    private void renderHand(GuiGraphics guiGraphics, GameStatePayload snap) {
        List<Integer> hand = snap.myHand();
        int n = hand.size();
        if (n == 0) return;
        logHandOnce(hand);

        List<Integer> selected = ClientDDZData.getInstance().getSelectedCards();

        for (int i = 0; i < n; i++) {
            boolean sel = selected.contains(hand.get(i));
            HandSlot slot = handSlot(i, n, sel);
            renderCardStack(guiGraphics, CardStacks.face(hand.get(i)),
                slot.bx(), slot.by(), RenderConfig.hudCardScale(), slot.fan(), true, HudHandMetrics.cardDepth(i));
        }
    }

    /** 一行小号卡牌（上家出的牌 / 底牌），从 startX 开始左对齐排列。 */
    private void renderCardsRow(GuiGraphics guiGraphics, List<Integer> cards, int centerY, int startX) {
        renderCardsRow(guiGraphics, cards, centerY, startX, RenderConfig.hudSmallCardGap);
    }

    private void renderCardsRow(GuiGraphics guiGraphics, List<Integer> cards, int centerY, int startX, int gap) {
        renderCardsRow(guiGraphics, cards, centerY, startX, RenderConfig.hudSmallCardW, gap);
    }

    /** 一行卡牌：卡宽 cardW（决定缩放）、间距 gap，从 startX 起左对齐，centerY 为牌行竖直中心。 */
    private void renderCardsRow(GuiGraphics guiGraphics, List<Integer> cards, int centerY,
                                int startX, int cardW, int gap) {
        if (cards == null || cards.isEmpty()) return;
        float sc = cardW * 16F / 10.2F;
        for (int i = 0; i < cards.size(); i++) {
            renderCardStack(guiGraphics, CardStacks.face(cards.get(i)),
                startX + i * gap + cardW / 2f, centerY, sc, 0F, false, HudHandMetrics.cardDepth(i));
        }
    }

    /** 小号牌行总宽度（间距 gap，牌宽 hudSmallCardW）。 */
    private int rowWidth(int n, int gap) {
        return gap * (n - 1) + RenderConfig.hudSmallCardW;
    }

    /**
     * 在 GUI 坐标系中渲染一张 3D 卡牌模型。px/py 为旋转支点：
     * 手牌扇形时传牌底中心（bottomPivot=true），卡牌行传牌中心（false）。
     *
     * <p>渲染链（NeoForge 1.21.1 ItemRenderer，JOML 右乘语义）：
     * <ul>
     *   <li>FaceBakery 把 0..16 模型烘焙到 0..1 空间（÷16）</li>
     *   <li>{@code renderStatic} 代码顺序先 apply display transform（gui rotX90）再 translate(-0.5)，
     *       但 PoseStack 为右乘，顶点实际先吃 translate(-0.5)（牌中心 (0.5,0.5,0.5)→原点）后吃 rotX90
     *       —— 牌中心旋转后仍在调用者原点</li>
     *   <li>牌面（模型 z=0..1，烘焙后 0..1）经 rotX90 转到屏幕 y 方向，scale(scale,-scale) 把倒置的牌面翻回正立</li>
     *   <li>牌底（模型 z=0）位于原点下方 0.5×scale（屏幕 y 向下）</li>
     * </ul>
     * 因此 x 无需补偿；中心支点 offsetY=0（牌中心落在 py），牌底支点 offsetY=0.5（把牌底上移回 py）。
     */
    private void renderCardStack(GuiGraphics guiGraphics, ItemStack stack, float px, float py,
                                 float scale, float rotationZ, boolean bottomPivot, float depth) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;

        float offsetY = bottomPivot ? 0.5F : 0F;

        PoseStack pose = guiGraphics.pose();
        pose.pushPose();
        pose.translate(px, py, depth);
        pose.mulPose(Axis.ZP.rotationDegrees(rotationZ));
        pose.translate(0F, -offsetY * scale, 0F);
        pose.scale(scale, -scale, 1);
        ItemRenderer renderer = mc.getItemRenderer();
        renderer.renderStatic(stack, ItemDisplayContext.GUI, 0xF000F0,
            OverlayTexture.NO_OVERLAY, pose, guiGraphics.bufferSource(), mc.level, 0);
        pose.popPose();
    }

    /** 卡牌 ID → 牌面文本（用于提示栏展示已选牌，方便确认顺子/对子等牌型）。 */
    private String rankLabel(int cardId) {
        return switch (DDZEngine.getRank(cardId)) {
            case 14 -> "A";
            case 15 -> "2";
            case 16 -> "小王";
            case 17 -> "大王";
            default -> String.valueOf(DDZEngine.getRank(cardId));
        };
    }

    private int handStartX() {
        return handLayout().startX();
    }

    /**
     * 手牌横向布局（间距、起点、扇形外扩）。手牌多且扇形角大时，最外侧牌的顶角会甩出屏幕，
     * 故把扇形外扩量一并纳入预算——见 {@link HudHandMetrics}。
     */
    private HudHandMetrics.Layout handLayout() {
        GameStatePayload snap = ClientDDZData.getInstance().getSnapshot();
        return HudHandMetrics.solve(
            viewport().width(),
            snap.myHand().size(),
            RenderConfig.hudCardW,
            RenderConfig.hudCardH(),
            RenderConfig.hudHandGap,
            RenderConfig.hudMinGap,
            RenderConfig.hudMaxWidthRatio,
            RenderConfig.hudFanAngle);
    }

    /** 手牌拱形偏移：第 i 张（共 n 张）牌的底部基准线像素偏移。中间牌（t=0）无偏移、最高，
     *  两端牌向下偏移 archHeightPixels，屏幕 Y 向下故正值为下沉。 */
    private static int archOffset(int i, int n) {
        if (n <= 1) return 0;
        float t = (i - (n - 1) / 2f) / ((n - 1) / 2f); // 归一化 -1..1，0 为中间
        return (int) (t * t * RenderConfig.hudArchPixels);
    }

    /** 手牌间距：默认 hudHandGap，牌太多放不下时自动压缩（不低于 hudMinGap）。 */
    private int handGap() {
        return handLayout().gap();
    }

    private int handBaseY() {
        return viewport().height() - RenderConfig.hudBottomMargin - RenderConfig.hudCardH();
    }

    private int countOnline(GameStatePayload snap) {
        int count = 0;
        for (boolean b : snap.online()) if (b) count++;
        return count;
    }

    /** 快照里的筹码物品显示名（如"钻石"）；未声明或物品不存在时回退为注册名。 */
    private static String chipName(GameStatePayload snap) {
        if (snap.betItem().isEmpty()) return "无";
        var item = net.minecraft.core.registries.BuiltInRegistries.ITEM.get(
            net.minecraft.resources.ResourceLocation.parse(snap.betItem()));
        return item.getDefaultInstance().getHoverName().getString();
    }

    private String playerName(GameStatePayload snap, int index) {
        if (index < 0 || index >= snap.playerNames().size()) return "?";
        String name = snap.playerNames().get(index);
        return name.isEmpty() ? "玩家" + (index + 1) : name;
    }
}
