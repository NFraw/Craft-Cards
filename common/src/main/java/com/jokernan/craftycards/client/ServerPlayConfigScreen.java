package com.jokernan.craftycards.client;

import com.jokernan.craftycards.game.server.ServerGameConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

/**
 * 玩法设置页（服务端参数，房主可改）。
 *
 * <p>此前游戏内只有音频配置界面，房主找不到底注、入局距离、旁观可见性这些玩法开关，
 * 只能手改 {@code config/crafty_cards/server.json}——本页补上这一块。</p>
 *
 * <p>数值用"循环按钮"而不是输入框：取值都是有限的小范围枚举，点一下换一档，
 * 既不可能输错、也不需要校验。改动在点「保存并生效」后写回文件
 * （单机/局域网里客户端与服务端共享同一份静态配置；连专用服务器时改的是本地文件，
 * 对服务器无效，页面会提示）。</p>
 */
public class ServerPlayConfigScreen extends ConfigScreenBase {
    /**
     * 可选档位（含说明），避免手输入错。
     *
     * <p>包级可见：{@link TableConfigScreen}（每桌配置）复用同一组档位，
     * 免得"全局默认值"与"本桌值"能选的档位不一致。</p>
     */
    static final Integer[] STAKE_CHOICES = {1, 2, 3, 5, 10};
    /**
     * 入局门槛档位：{@code 0 = 自动}（底注 × 6），其余是直接指定的数量。
     *
     * <p>给到 100 万是有意的：门槛只参与"背包够不够"的审核，房主可能想开高额桌或做活动门槛。
     * 本项目的界面统一用循环按钮（不可能输错、不需要校验），所以高段按倍数跳；
     * 想要列表里没有的精确值就手改 {@code server.json}（上不封顶）。档位里 6/12/18/30/60
     * 正是各底注档（1/2/3/5/10）对应的"自动"值，方便对照。</p>
     */
    static final Integer[] ENTRY_CHOICES = {
        0, 6, 12, 18, 30, 60, 120, 300, 600, 1200, 3000, 6000, 12000, 30000, 60000, 120000, 600000, 1000000};
    private static final Integer[] JOIN_RADIUS_CHOICES = {4, 6, 8, 12, 16, 24, 32};
    private static final Integer[] SPECTATE_RADIUS_CHOICES = {0, 8, 16, 24, 32, 48};
    private static final Integer[] IDLE_SECONDS_CHOICES = {0, 60, 300, 600, 1200, 1800};
    /**
     * 每轮出牌限时（秒）：超时未出牌按判负结算。
     *
     * <p><b>不含 0</b>：关闭是上面那个「每轮出牌限时」开关的事。让"关"同时藏在档位里
     * （转一圈到"不限时"）与开关里，就会出现两处状态、两处判断——文件里写 0 到底是
     * "关掉了"还是"选了不限时"分不清。所以约定：<b>配置里 {@code turnTimeoutTicks = 0}
     * 就是关闭</b>，界面上的开关负责写 0 / 写回秒数。</p>
     */
    private static final Integer[] TURN_TIMEOUT_SECONDS = {15, 30, 45, 60, 90};

    /** 当前编辑中的值（点保存才写回）。 */
    private int stake = ServerGameConfig.chipStake;
    private int entryCount = ServerGameConfig.chipEntryCount;
    private boolean chipsRequired = ServerGameConfig.chipsRequired;
    private double joinRadius = ServerGameConfig.joinRadius;
    private double spectateRadius = ServerGameConfig.spectateRadius;
    private int idleTicks = ServerGameConfig.idleTimeoutTicks;
    /** 每轮限时是否开启 + 开启时用多少刻。关闭 = 保存时写 0（见 TURN_TIMEOUT_SECONDS 的说明）。 */
    private boolean turnTimeoutEnabled = ServerGameConfig.turnTimeoutTicks > 0;
    private int turnTimeout = ServerGameConfig.turnTimeoutTicks > 0
        ? ServerGameConfig.turnTimeoutTicks : 600;
    private boolean spectatorsSeeCards = ServerGameConfig.spectatorsSeeCards;
    private boolean participantsSeeFaces = ServerGameConfig.participantsSeeFaces;

    public ServerPlayConfigScreen(Screen parent) {
        super(parent, "玩法设置（房主）", "赌注、距离、超时与观战可见性");
    }

    @Override
    protected void buildRows() {
        list.header("赌注");
        list.note("筹码按桌存：底下的三项只是新桌子的默认值，每张桌可在桌前 Shift+右键单独改");
        list.row(toggleRow("需要筹码", "关掉则纯娱乐局：不必配筹码、不收押注、结算不转移物品（全世界的桌子一起关）",
            () -> chipsRequired, v -> chipsRequired = v));
        if (!chipsRequired) {
            // 关掉筹码后再显示底注/门槛只会让人以为它们还在起作用
            list.note("当前不玩筹码：入局只需右键牌桌，底注与门槛都不生效");
        } else {
            list.row(choiceRow("入局底注（每人押注数）", "新桌默认值；结算按 底分 × 倍数 × 底注",
                STAKE_CHOICES, () -> stake, v -> stake = v, v -> v + " 个"));
            list.row(choiceRow("入局筹码数（门槛）", "新桌默认值；背包里备够才允许入局，0 = 自动（底注 × 6）",
                ENTRY_CHOICES, () -> entryCount, v -> entryCount = v,
                v -> v == 0 ? "自动" : v + " 个"));
            list.note(entryNote());
        }

        list.header("距离与超时");
        list.row(choiceRow("入局 / 观战距离（格）", "超出此距离不能入局、也不能开启观战",
            JOIN_RADIUS_CHOICES, () -> (int) joinRadius, v -> joinRadius = v, v -> v + " 格"));
        list.row(choiceRow("走近自动共享手牌半径", "0 = 必须 Shift+右键才能观战（推荐，防作弊）",
            SPECTATE_RADIUS_CHOICES, () -> (int) spectateRadius, v -> spectateRadius = v,
            v -> v == 0 ? "关（推荐）" : v + " 格"));
        list.row(choiceRow("空闲销毁", "多久无人操作就销毁牌局并退还筹码",
            IDLE_SECONDS_CHOICES, () -> idleTicks / 20, v -> idleTicks = v * 20,
            v -> v == 0 ? "不超时" : v + " 秒"));
        list.row(toggleRow("每轮出牌限时", "关掉则不限时：轮到的人可以一直想（空闲销毁仍然有效）",
            () -> turnTimeoutEnabled, v -> turnTimeoutEnabled = v));
        if (turnTimeoutEnabled) {
            list.row(choiceRow("每轮限时秒数", "超时未出牌按判负结算（判负者赔其余两家各 门槛 / 2）",
                TURN_TIMEOUT_SECONDS, () -> turnTimeout / 20, v -> turnTimeout = v * 20,
                v -> v + " 秒"));
            list.note("牌桌上会显示当前出牌人的剩余秒数（最后 10 秒转橙、5 秒转红）");
        } else {
            list.note("当前不限时：出牌/叫分阶段不会因超时判负，牌桌上也不显示倒计时");
        }

        list.header("看牌权限（防作弊）");
        list.row(toggleRow("观战者可见牌面", "关掉则观战者只能看到牌背与张数",
            () -> spectatorsSeeCards, v -> spectatorsSeeCards = v));
        list.row(toggleRow("参局者互看牌面", "默认关（防作弊）；熟人娱乐局可以开",
            () -> participantsSeeFaces, v -> participantsSeeFaces = v));
        list.note("牌面可见性由服务端逐份快照决定，改客户端看不到不该看的牌");
    }

    /**
     * 门槛那一行的说明：讲清"自动值"以及手动值是否低于它。
     *
     * <p>手动值低于派生值<b>不做拒绝</b>——低额桌是房主的自由（接受"赔不出时少赔"），
     * 但必须提示，否则玩家要到结算时才发现有人赔不出来。</p>
     */
    private String entryNote() {
        int auto = Math.max(1, stake) * 6;
        if (entryCount <= 0) {
            return "门槛自动 = 底注 × 6 = " + auto + " 个（无炸弹时最坏的一局）";
        }
        if (entryCount < auto) {
            return "手动 " + entryCount + " 个 < 自动值 " + auto + " 个：输家可能赔不出，结算会少赔并提示";
        }
        return "手动 " + entryCount + " 个（≥ 自动值 " + auto + " 个，赔得起）";
    }

    @Override
    protected void buildFooter() {
        addFooterButton("保存并生效", () -> {
            ServerGameConfig.apply(stake, entryCount, chipsRequired, joinRadius, spectateRadius, idleTicks,
                // 关闭 = 写 0：配置里只有 tick 数这一个真相，界面上的开关只是它的读法/写法
                turnTimeoutEnabled ? turnTimeout : 0, spectatorsSeeCards, participantsSeeFaces);
            rebuild();
            status = "已保存：" + ServerGameConfig.describeCurrent()
                + (isDedicatedServer() ? "（注意：这是本地文件，专用服务器需在服务端改）" : "");
        });
    }

    @Override
    protected String hint() {
        return isDedicatedServer()
            ? "当前连着专用服务器：这里的改动只写入本地文件，服务器需自行配置"
            : "点档位按钮换档 · 保存后立即生效 · 文件 " + ServerGameConfig.file();
    }

    /** 连专用服务器时本地改动不生效，用橙色提醒。 */
    @Override
    protected int hintColor() {
        return isDedicatedServer() ? 0xFFFFAA00 : 0xFF808080;
    }

    /** 是否连着专用服务器（此时本地改动不影响服务器）。 */
    private boolean isDedicatedServer() {
        Minecraft mc = Minecraft.getInstance();
        return mc.getSingleplayerServer() == null;
    }
}
