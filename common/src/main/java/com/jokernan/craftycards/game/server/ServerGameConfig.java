package com.jokernan.craftycards.game.server;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.jokernan.craftycards.CCReference;
import com.jokernan.craftycards.platform.GamePaths;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * 服务端玩法配置：{@code config/crafty_cards/server.json}。双端都会加载，
 * 但只有服务端读取（快照按接收者构建在这里发生）；客户端改了不生效。
 *
 * <p>与 {@code visual.json}（客户端渲染参数，{@code /craftycards reload} 热更新）分开：
 * 这里的项决定"谁能看到什么牌"与旁观范围，属于服务器管理员的玩法开关，修改后重启生效。
 */
public final class ServerGameConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    /** 未参与牌局的玩家（旁观者）能否看到各玩家手里的牌面。false 时旁观者只能看到牌背与张数。 */
    public static boolean spectatorsSeeCards = true;

    /** 参与牌局的玩家能否看到其他玩家手里的牌面（开放手牌模式，娱乐局可开）。默认 false 防作弊。 */
    public static boolean participantsSeeFaces = false;

    /**
     * "走近就自动共享手牌"的半径（方块，距牌桌中心）。<b>默认 0 = 关闭</b>。
     *
     * <p>默认关闭是刻意的：只要走近就能看到所有人手牌，等于把牌摊在公共频道上——
     * 任何人在牌桌附近用改过的客户端都能看到三家手牌。要看牌必须<b>主动右键牌桌开始观战</b>
     * （见 {@link DDZSession#handleWatch}），那条路径不受本项影响。</p>
     *
     * <p>只想在小范围（如私人服务器的小游戏区）恢复"走近即可看"，把它调大即可。</p>
     */
    public static double spectateRadius = 0.0;

    /**
     * 定期给旁观者补发快照的间隔（游戏刻，20 刻 = 1 秒）。0 = 只在有操作时下发。
     *
     * <p>没有这个补发的话，观众走进范围后要一直等到下一次出牌/过牌才收得到数据，
     * 期间世界里什么都看不到——"走过去看牌"就落空了。补发只刷新状态，
     * 牌的位置仍由客户端实体每秒多帧插值，不影响流畅度。
     */
    public static int spectateRefreshTicks = 20;

    /**
     * 多久没有任何操作就销毁牌局（游戏刻，20 刻 = 1 秒；默认 12000 = 10 分钟）。0 = 不超时。
     *
     * <p>防止有人挂机导致牌局（以及每个客户端的牌面渲染）长期占用资源。销毁时走
     * {@code disband}：回收各玩家的斗地主扑克、广播收尾。
     */
    public static int idleTimeoutTicks = 12000;

    /**
     * 每轮限时（游戏刻，20 刻 = 1 秒；默认 600 = 30 秒）。<b>0 = 不限时（关闭）</b>。
     *
     * <p>轮到某人叫分/出牌后，在这段时间内没有任何操作就按<b>判负</b>结算（赔其余两家各 门槛/2，
     * 复用 {@code DDZSession#pay} 的池子优先赔付）。与 {@link #idleTimeoutTicks} 的区别：
     * 后者是「整桌没人动」的兜底销毁（退款收场），这里是「某一个人卡住」的判罚。</p>
     *
     * <p>界面上它是一个「每轮出牌限时」开关 + 秒数档位（玩法设置页），但<b>配置里只有这一个字段</b>：
     * 关闭就是写 0、开启就写回秒数对应的刻数。同一件事有两个开关（开关 + 档位里的"不限时"）
     * 迟早会出现"文件写 0 到底是关掉了还是选了不限时"的疑问。</p>
     *
     * <p>开启时服务端会把"这一轮还剩多少刻"随快照下发（{@code GameStatePayload.turnRemainingTicks}），
     * HUD 据此在阶段那一行后面画倒计时；0（关闭）时下发 -1，客户端什么都不画。</p>
     */
    public static int turnTimeoutTicks = 600;

    /**
     * 入局/观战的最大距离（方块，距牌桌中心）。超出直接拒绝。
     *
     * <p>没有这道校验的话，改过的客户端可以带着任意坐标发 JOIN：在世界任意角落"入局"（拿到扑克、
     * 占住座位），也可以拿随机坐标刷出大量空牌局（每个会话都要等空闲超时才回收）。原版右键方块
     * 本来就有触及距离，这里是服务端侧的对应校验。</p>
     */
    public static double joinRadius = 8.0;

    /**
     * 可作本局筹码的物品 id 列表。第一个声明的人手持其中之一（潜行+右键牌桌）即定下本局筹码。
     *
     * <p>写成可配置的列表而不是硬编码，是为了让服务器管理员能加入模组物品
     * （例如某个模组的"金币"）。彼此之间<b>不换算价值</b>：本局只用其中一种，
     * 赔的就是那一种物品。</p>
     */
    public static List<String> chipItems = List.of(
        "minecraft:diamond",
        "minecraft:gold_ingot",
        "minecraft:iron_ingot",
        "minecraft:emerald",
        "minecraft:netherite_ingot");

    /**
     * 每人入场押注的筹码数量（底注）——<b>由服务器房主配置</b>，决定本局赌注的大小。
     *
     * <p>押注在入局时收进物品池（保留附魔等组件），结算时按
     * {@code 底分 × 倍数 × 底注} 转移，其中底分 = 叫分、倍数 = 2^(炸弹+火箭) × 春天系数。</p>
     */
    public static int chipStake = 1;

    /**
     * 房主可直接指定的<b>入局筹码数</b>（门槛）。<b>0 = 自动</b>，即由底注派生。
     *
     * <p>为什么保留自动档：派生值 = {@code 底注 × 6} 覆盖的是"无炸弹时最坏的一局"，
     * 默认走它就不会出现"底注 3 却要求门槛 2"这类无意的自相矛盾。但房主也常常想要
     * 明确的门槛：高额桌（门槛远高于派生值，逼玩家先攒够筹码）或宽松桌（低于派生值，
     * 接受"赔不出时少赔并提示"）。</p>
     *
     * <p>上限只受 {@code int} 限制：界面上档位到 100 万，手改 {@code server.json} 可以更大。
     * 门槛只参与"背包里够不够"的审核、不参与任何运算，故没有溢出问题；实用上限取决于
     * 玩家能持有多少（原版背包 36 格 × 64 = 2304）。</p>
     */
    public static int chipEntryCount = 0;

    /**
     * 本局是否需要筹码（赌注）。<b>默认需要</b>。
     *
     * <p>{@code false} 时牌桌变成纯娱乐局：不必声明筹码、不审核背包数量、入局不收押注，
     * 结算也不做任何物品转移——玩家右键牌桌即可入局开局。适合"只想打牌、不想动物品"的场合
     * （创造模式、建筑服、活动用的临时牌桌）。</p>
     *
     * <p>关掉后 {@link #chipItems} 与 {@link #chipStake} 仍会被读取，但不参与任何判定；
     * {@link #requiredChips()} 返回 0，快照照发 0，客户端据此把界面切成"无需筹码"。</p>
     */
    public static boolean chipsRequired = true;

    /**
     * 入局所需的筹码数量（门槛）：{@link #chipEntryCount} > 0 时直接用它，否则由底注派生；
     * <b>不需要筹码时返回 0</b>。
     *
     * <p>派生值 = {@code 底注 × 6}，算式是"无炸弹时最坏的一局"：叫满 3 分（底分上限）
     * × 2（地主一人对两农民、最多赔两份）× 底注。注意炸弹会让倍数翻倍、赔付可能超过
     * 本门槛——那种情况下少赔并提示（见 {@code DDZSession.distributeBets}），
     * 这是用实物结算倍数赌注的固有限制。</p>
     *
     * <p>返回 0 表示本局不玩赌注（{@link #chipsRequired} 为 false）：入局不校验、不收押注、
     * 结算不转移物品。用 0 而不是另加一个布尔参数，是因为快照本来就要下发"入局需要几个"，
     * 0 天然就是"这桌不玩赌注"的信号（与 {@code myIndex = -1} 表示旁观者同一套做法）。</p>
     */
    public static int requiredChips() {
        if (!chipsRequired) return 0;
        return chipEntryCount > 0 ? chipEntryCount : derivedRequiredChips();
    }

    /** 由底注派生的门槛（{@code 底注 × 6}）：界面用它显示"自动"值、并提示手动值是否低于它。 */
    public static int derivedRequiredChips() {
        return Math.max(1, chipStake) * 6;
    }

    private ServerGameConfig() {}

    /** 从配置文件加载；文件缺失时按默认值写出。读取出错回退默认值，不影响游戏。 */
    public static void load() {
        Data data = new Data();
        try {
            if (Files.exists(file())) {
                data = GSON.fromJson(Files.readString(file()), Data.class);
            }
        } catch (Exception e) {
            CCReference.LOG.warn("读取服务端配置失败，使用默认值: {}", e.toString());
        }
        if (data == null) data = new Data();
        spectatorsSeeCards = data.spectatorsSeeCards;
        participantsSeeFaces = data.participantsSeeFaces;
        spectateRadius = data.spectateRadius;
        spectateRefreshTicks = data.spectateRefreshTicks;
        turnTimeoutTicks = data.turnTimeoutTicks;
        idleTimeoutTicks = data.idleTimeoutTicks;
        joinRadius = data.joinRadius;
        chipItems = List.copyOf(data.chipItems);
        chipStake = data.chipStake;
        chipEntryCount = data.chipEntryCount;
        chipsRequired = data.chipsRequired;
        save();
        CCReference.LOG.info("Crafty Cards 服务端配置已加载: {}", file());
    }

    /**
     * 应用界面上的改动并写回配置文件。
     *
     * <p>单机/局域网里客户端与服务端是同一个 JVM，静态字段共享，所以调用方改完立即生效；
     * 连专用服务器时改的是本地文件、对服务器无影响——界面需就此给出提示。</p>
     */
    public static void apply(int chipStakeValue, int chipEntryCountValue, boolean chipsRequiredValue,
                            double joinRadiusValue, double spectateRadiusValue, int idleTimeoutValue,
                            int turnTimeoutValue, boolean spectatorsSeeCardsValue,
                            boolean participantsSeeFacesValue) {
        chipStake = Math.max(1, chipStakeValue);
        chipEntryCount = Math.max(0, chipEntryCountValue);   // 0 = 自动（底注 x 6）
        chipsRequired = chipsRequiredValue;
        joinRadius = Math.max(1.0, joinRadiusValue);
        spectateRadius = Math.max(0.0, spectateRadiusValue);
        idleTimeoutTicks = Math.max(0, idleTimeoutValue);
        turnTimeoutTicks = Math.max(0, turnTimeoutValue);
        spectatorsSeeCards = spectatorsSeeCardsValue;
        participantsSeeFaces = participantsSeeFacesValue;
        save();
        // 立刻给所有牌局重推一份快照：快照里的底注/门槛/倒计时都是按配置算的，而座上玩家平时
        // 收不到周期性补发——不推这一下，改完配置界面上的数字要等到下一次出牌才更新
        // （最刺眼的是"每轮限时已关，倒计时还冻在 0s"）。
        DDZTableManager.getInstance().refreshAll();
    }

    /** 当前值（供界面初始化）。 */
    public static String describeCurrent() {
        String stake = chipsRequired
            ? "底注 " + chipStake + " · 门槛 " + requiredChips()
                + (chipEntryCount > 0 ? "（手动）" : "（自动）")
            : "不需要筹码";
        return stake + " · 入局距离 " + (int) joinRadius + " · 旁观半径 "
            + (int) spectateRadius + " · 空闲超时 " + (idleTimeoutTicks / 20) + "s"
            + " · 每轮限时 " + (turnTimeoutTicks > 0 ? (turnTimeoutTicks / 20) + "s" : "不限时");
    }

    /** 配置文件路径（界面显示用）。 */
    public static Path file() {
        return GamePaths.config("server.json");
    }

    /**
     * 把<b>当前值</b>写回配置文件。
     *
     * <p>注意不能写 {@code new Data()}：那样序列化的是 {@code Data} 字段的字面量默认值，
     * 而不是刚才加载/改动的值——表现为"手改 server.json 一重启就变回默认"，
     * 界面上点「保存并生效」也永远不落盘（曾经如此）。{@code Data} 里的字面量只用作
     * <b>反序列化缺省值</b>（JSON 里缺某个键时用字面量，见 Gson 的行为）。</p>
     */
    private static void save() {
        try {
            Files.createDirectories(file().getParent());
            Files.writeString(file(), GSON.toJson(snapshot()));
        } catch (IOException e) {
            CCReference.LOG.warn("保存服务端配置失败: {}", e.toString());
        }
    }

    /** 当前静态配置的一份拷贝，供序列化写出（Gson 不处理 static 字段）。 */
    private static Data snapshot() {
        Data data = new Data();
        data.spectatorsSeeCards = spectatorsSeeCards;
        data.participantsSeeFaces = participantsSeeFaces;
        data.spectateRadius = spectateRadius;
        data.spectateRefreshTicks = spectateRefreshTicks;
        data.turnTimeoutTicks = turnTimeoutTicks;
        data.idleTimeoutTicks = idleTimeoutTicks;
        data.joinRadius = joinRadius;
        data.chipItems = List.copyOf(chipItems);
        data.chipStake = chipStake;
        data.chipEntryCount = chipEntryCount;
        data.chipsRequired = chipsRequired;
        return data;
    }

    /** Gson 序列化载体（Gson 不处理 static 字段）；缺省值必须写成字面量，不能引用 static 字段。 */
    private static class Data {
        public boolean spectatorsSeeCards = true;
        public boolean participantsSeeFaces = false;
        public double spectateRadius = 0.0;
        public int spectateRefreshTicks = 20;
        public int turnTimeoutTicks = 600;
        public int idleTimeoutTicks = 12000;
        public double joinRadius = 8.0;
        public List<String> chipItems = List.of(
            "minecraft:diamond",
            "minecraft:gold_ingot",
            "minecraft:iron_ingot",
            "minecraft:emerald",
            "minecraft:netherite_ingot");
        public int chipStake = 1;
        /** 手动指定的入局门槛；0 = 自动（底注 × 6）。 */
        public int chipEntryCount = 0;
        /** 是否需要筹码；老配置文件里没有这个键时按 true（保持原有赌注玩法）。 */
        public boolean chipsRequired = true;
    }
}
