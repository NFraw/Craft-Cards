package com.jokernan.craftycards.client;

import com.jokernan.craftycards.game.server.ServerGameConfig;
import com.jokernan.craftycards.network.payload.GameStatePayload;
import com.jokernan.craftycards.network.payload.PlayerActionPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;

/**
 * 本桌筹码配置界面（房主专用）：筹码类型 / 底注 / 入局门槛 / 本桌启用筹码。
 *
 * <p><b>为什么是"服务端说了算"</b>：这个界面开不开，由服务端快照里的
 * {@code configOpen}（= 那把互斥锁在不在你手上）决定，客户端只负责显示。所以：</p>
 * <ul>
 *   <li>别人已经开着 → 服务端不下发 {@code configOpen}，界面根本不会开（不会出现两个人同改一桌）；</li>
 *   <li>锁超时/被接管/牌局结束 → 服务端补一份 {@code configOpen = false} 的快照，
 *       {@link #tick()} 看到就把界面关掉，不会停在一个"点了保存没反应"的死界面上。</li>
 * </ul>
 *
 * <p>四项的取值先放在本地编辑，点「保存」才发 {@code SAVE_TABLE_CONFIG}；服务端<b>全量重校验</b>
 * （房主身份、锁、是否还有人入局、筹码物品是否在白名单里），回执写在快照的 {@code feedback} 里，
 * 由本界面当状态行显示——配置界面开着的时候聊天栏是看不见的（HUD 整个不画）。</p>
 *
 * <p>候选筹码物品读的是<b>本机</b> {@code server.json} 的 {@code chipItems}：连专用服务器时
 * 它与服务端的那份无关，所以服务端还会再校验一次（不在白名单里会被明确拒绝，而不是静默生效）。</p>
 */
public class TableConfigScreen extends ConfigScreenBase {
    /** 底注档位：与玩法设置页共用同一组（那一页是"全局默认值"，这里是"本桌"）。 */
    private static final Integer[] STAKE_CHOICES = ServerPlayConfigScreen.STAKE_CHOICES;
    /** 入局门槛档位：0 = 自动（底注 × 6）。 */
    private static final Integer[] ENTRY_CHOICES = ServerPlayConfigScreen.ENTRY_CHOICES;

    /** 本地编辑中的值（点保存才发出去）。 */
    private String chipItem;
    private int stake;
    private int entryCount;
    private boolean chipsEnabled;
    /** 最近一次收到的服务端回执：变了就显示成状态行。 */
    private String lastFeedback;

    public TableConfigScreen() {
        super(null, "本桌筹码配置", "房主可改 · 只影响这一张桌子");
        GameStatePayload snap = ClientDDZData.getInstance().getSnapshot();
        if (snap != null) {
            this.chipItem = snap.betItem();
            this.stake = Math.max(1, snap.betCount());
            // 门槛用本桌的**原始**值（0 = 自动），不是实际生效值：否则"自动"与"手动填了正好等于
            // 派生值"分不出来，房主不改这一项点保存也会把手动值改成自动
            this.entryCount = Math.max(0, snap.tableEntryCount());
            this.chipsEnabled = snap.tableChipsEnabled();
            this.lastFeedback = snap.feedback();
        } else {
            this.chipItem = "";
            this.stake = Math.max(1, ServerGameConfig.chipStake);
            this.entryCount = ServerGameConfig.chipEntryCount;
            this.chipsEnabled = true;
            this.lastFeedback = "";
        }
    }

    @Override
    protected void buildRows() {
        GameStatePayload snap = ClientDDZData.getInstance().getSnapshot();
        list.header("筹码");
        if (!ServerGameConfig.chipsRequired) {
            // 闸门关掉时"本桌启用筹码"这一项怎么点都没用，必须说清楚，否则房主会以为界面坏了
            list.note("服务器总闸门已关（server.json 的 chipsRequired）：全世界都不玩筹码，本桌开关不生效");
        }
        list.row(choiceRow("筹码类型", "本桌用它结算；候选来自本机 server.json 的 chipItems，服务器会再校验",
            chipChoices(chipItem), () -> chipItem, v -> chipItem = v, TableConfigScreen::chipLabel));
        list.row(choiceRow("入局底注（每人押注数）", "结算按 底分 × 倍数 × 底注",
            STAKE_CHOICES, () -> stake, v -> stake = v, v -> v + " 个"));
        list.row(choiceRow("入局筹码数（门槛）", "背包里备够这么多才允许入局；0 = 自动（底注 × 6）",
            ENTRY_CHOICES, () -> entryCount, v -> entryCount = v, v -> v == 0 ? "自动" : v + " 个"));
        list.row(toggleRow("本桌启用筹码", "关掉则这张桌子是纯娱乐局：不收押注、结算不转移物品",
            () -> chipsEnabled, v -> chipsEnabled = v));
        list.note(entryNote());
        list.note(chipNote(snap));
    }

    /**
     * 门槛那一行的说明：讲清"自动值"以及手动值是否低于它。
     *
     * <p>手动值低于派生值<b>不做拒绝</b>（低额桌是房主的自由，接受"赔不出时少赔"），但必须提示，
     * 否则玩家要到结算时才发现有人赔不出来。</p>
     */
    private String entryNote() {
        int auto = Math.max(1, stake) * 6;
        if (!chipsEnabled) return "本桌不玩筹码：门槛与底注都不生效，右键牌桌即可入局";
        if (entryCount <= 0) return "门槛自动 = 底注 × 6 = " + auto + " 个（无炸弹时最坏的一局）";
        if (entryCount < auto) {
            return "手动 " + entryCount + " 个 < 自动值 " + auto + " 个：输家可能赔不出，结算会少赔并提示";
        }
        return "手动 " + entryCount + " 个（≥ 自动值 " + auto + " 个，赔得起）";
    }

    /** 筹码那一行的补充说明：还没选筹码时明确告诉房主"不选就没人入得了局"。 */
    private String chipNote(GameStatePayload snap) {
        if (!chipsEnabled || !ServerGameConfig.chipsRequired) return "";
        if (chipItem.isEmpty()) return "还没选筹码：玩家右键牌桌会被拒绝（本桌需要筹码）";
        return "本桌筹码：" + chipItem;
    }

    @Override
    protected void buildFooter() {
        addFooterButton("保存", () -> {
            GameStatePayload snap = ClientDDZData.getInstance().getSnapshot();
            if (snap == null) {
                status = "牌局状态已丢失，请重新打开";
                return;
            }
            ClientDDZData.getInstance().send(new PlayerActionPayload(snap.table(),
                PlayerActionPayload.Action.SAVE_TABLE_CONFIG, stake, List.of(entryCount),
                chipItem, chipsEnabled));
            status = "已发送保存请求…";
        });
    }

    @Override
    protected String hint() {
        return "点档位按钮换档 · 保存后立即生效（只影响这一张桌）";
    }

    @Override
    public void render(net.minecraft.client.gui.GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        // 服务端回执优先显示（"已保存：…" / "筹码物品不在服务器的白名单里"）
        String feedback = feedbackOf();
        if (!feedback.equals(lastFeedback)) {
            lastFeedback = feedback;
            if (!feedback.isEmpty()) status = feedback;
        }
        super.render(guiGraphics, mouseX, mouseY, partialTick);
    }

    /**
     * 服务端把锁收了（超时 / 被别人接管 / 牌局结束）就把界面关掉。
     *
     * <p>不能留着：界面上的「保存」之后只会收到"配置已超时"，玩家会以为卡死了。</p>
     */
    @Override
    public void tick() {
        GameStatePayload snap = ClientDDZData.getInstance().getSnapshot();
        if (snap == null || !snap.configOpen()) {
            String why = snap == null ? "" : snap.feedback();
            Minecraft mc = Minecraft.getInstance();
            if (mc.screen == this) mc.setScreen(null);
            if (!why.isEmpty() && mc.player != null) {
                mc.player.displayClientMessage(Component.literal(why), true);
            }
        }
    }

    /** 「返回」/ ESC：把自己的配置锁放掉（服务端只清自己持有的那把，不碰别人）。 */
    @Override
    public void onClose() {
        GameStatePayload snap = ClientDDZData.getInstance().getSnapshot();
        if (snap != null && snap.configOpen() && Minecraft.getInstance().getConnection() != null) {
            ClientDDZData.getInstance().send(new PlayerActionPayload(snap.table(),
                PlayerActionPayload.Action.CLOSE_TABLE_CONFIG, 0, List.of()));
        }
        super.onClose();
    }

    private String feedbackOf() {
        GameStatePayload snap = ClientDDZData.getInstance().getSnapshot();
        return snap == null ? "" : snap.feedback();
    }

    /**
     * 筹码候选项：{@code ""}（未选择）+ 本机白名单；当前值不在白名单里时也保留它，
     * 否则房主一进界面点一下就把服务器上的值换掉了，而他根本没想改。
     */
    private static String[] chipChoices(String current) {
        List<String> ids = new ArrayList<>();
        ids.add("");
        for (String id : ServerGameConfig.chipItems) {
            if (id != null && !id.isEmpty() && !ids.contains(id)) ids.add(id);
        }
        if (current != null && !current.isEmpty() && !ids.contains(current)) ids.add(current);
        return ids.toArray(new String[0]);
    }

    /** 注册名 → 物品显示名（认不出来的就原样显示注册名，至少不是空白）。 */
    private static String chipLabel(String id) {
        if (id == null || id.isEmpty()) return "未选择";
        ResourceLocation location = ResourceLocation.tryParse(id);
        if (location == null) return id;
        Item item = BuiltInRegistries.ITEM.get(location);
        if (item == null || item == Items.AIR) return id;
        return item.getDescription().getString();
    }
}
