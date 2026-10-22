package com.jokernan.craftycards.uiprobe;

import com.jokernan.craftycards.client.ZJHControls;

import java.util.ArrayList;
import java.util.List;

/**
 * 炸金花 HUD 的<b>阶段目录</b>：把界面上真实会出现的每一种"排面"各取一个样本。
 *
 * <p>选项文字不在这里硬编码——它由 {@link ZJHControls#buttons} 与 {@link ZJHControls#label}
 * 现算，和 {@code ZJHClient.choices()} 读的是同一份策略。唯一的重复是那几个固定标签
 * （准备 / 开始本局 / 离开牌局 / 比牌 / 再来一局），它们本来就直接写在 {@code choices()} 里；
 * {@code ZjhUiProbeTest} 会把这份目录与"策略侧应有的集合"逐项对齐，改漏一处就会红。</p>
 *
 * <p>{@code players} 只影响结算页的行数与行高，所以结算阶段按 2–6 人各来一份；
 * 对局阶段的 {@code players} 只用于"比牌目标"那一行的选项个数。</p>
 */
record ZjhUiStage(String id, String title, Kind kind, int revealed, int players,
                  List<String> options, String note) {

    enum Kind {
        /** 等待开局：准备 / 开始本局 / 离开牌局。 */
        WAITING,
        /** 对局中：明牌 / 跟注 / 加注 / 加倍 / 弃牌（+ 比牌）。 */
        PLAYING,
        /** 选牌模式：只有提示行，没有选项行。 */
        SELECT_CARDS,
        /** 比牌选目标：选项行变成对手名字。 */
        PICK_TARGET,
        /** 结算：整屏遮罩 + 礼花 + 玩家行 + 底部操作行。 */
        SETTLED
    }

    /** 该阶段是否有选项行（选牌模式没有，靠左键点牌 + 右键牌桌确认）。 */
    boolean hasOptionRow() {
        return !options.isEmpty();
    }

    /** 构造对局阶段的选项：与 {@code ZJHClient.choices()} 完全同源。 */
    private static List<String> playingOptions(int revealed, int ante, int base, boolean compare) {
        List<String> list = new ArrayList<>();
        for (ZJHControls.Button b : ZJHControls.buttons(revealed, ante, base)) list.add(ZJHControls.label(b));
        if (compare) list.add("比牌 " + ((long) base * (revealed > 0 ? 4 : 2)));
        return List.copyOf(list);
    }

    private static ZjhUiStage playing(String id, String title, int revealed, int ante, int base,
                                      boolean compare, String note) {
        return new ZjhUiStage(id, title, Kind.PLAYING, revealed, 3,
            playingOptions(revealed, ante, base, compare), note);
    }

    private static ZjhUiStage settled(String id, int players, boolean viewerWins) {
        // 结算页没有选项行：服务端已自动结算并退局，右键任意位置关闭
        String title = "结算（" + (viewerWins ? "自己赢" : "自己输") + "）";
        return new ZjhUiStage(id, title, Kind.SETTLED, 0, players, List.of(),
            players + " 人 · " + (viewerWins ? "有礼花" : "无礼花") + " · 底部只有一行关闭提示");
    }

    /** 全部阶段，顺序即报告顺序（也即玩家一局里会依次看到的顺序）。 */
    static List<ZjhUiStage> catalogue() {
        List<ZjhUiStage> list = new ArrayList<>();

        // ---- 等待开局：加入即就绪，人数够自动开局，所以唯一的选项是「离开牌局」
        list.add(new ZjhUiStage("waiting", "等待开局（加入即就绪）", Kind.WAITING, 0, 3,
            List.of("离开牌局"), "单项：只有离开"));
        list.add(new ZjhUiStage("waiting-reason", "等待开局（服务端给了原因）", Kind.WAITING, 0, 3,
            List.of("离开牌局"), "第三行换成 waitingReason 文本，布局不变"));

        // ---- 对局中：暗牌（最小字号档，费用 1/2/4）
        list.add(playing("playing-dark-r1", "对局 · 暗牌 · 第 1 轮", 0, 1, 1, false,
            "5 项，第三轮前没有比牌"));
        list.add(playing("playing-dark-r3", "对局 · 暗牌 · 可比牌", 0, 1, 1, true,
            "6 项：最宽的一档"));
        // ---- 对局中：明牌 1/2 张（费用翻倍，明牌入口仍在）
        list.add(playing("playing-revealed1", "对局 · 明牌 1/3", 1, 1, 1, true,
            "费用翻倍，明牌仍可选"));
        list.add(playing("playing-revealed2", "对局 · 明牌 2/3", 2, 1, 1, true,
            "费用翻倍，明牌仍可选"));
        // ---- 对局中：三张全开（明牌入口撤掉）
        list.add(playing("playing-revealed3", "对局 · 明牌 3/3", 3, 1, 1, true,
            "5 项：明牌已撤"));
        // ---- 大盲注档：数字变长，考验选项行宽度预算
        list.add(playing("playing-bigblind", "对局 · 大注（底注 10 / 明注 50）", 1, 10, 50, true,
            "三位数费用：跟注 100 / 加注 40 / 加倍 80 / 比牌 200"));
        list.add(playing("playing-cap", "对局 · 封顶档（底注 100 / 明注 500）", 0, 100, 500, true,
            "四位数费用：跟注 500 / 比牌 1000"));

        // ---- 选牌模式：没有选项行
        list.add(new ZjhUiStage("selecting-cards", "选牌模式", Kind.SELECT_CARDS, 1, 3,
            List.of(), "无选项行，提示行说明左键选牌"));

        // ---- 比牌选目标：选项行是对手名字
        list.add(new ZjhUiStage("target-2", "比牌选目标（2 人局）", Kind.PICK_TARGET, 0, 2,
            List.of("Player2"), "1 个目标"));
        list.add(new ZjhUiStage("target-3", "比牌选目标（3 人局）", Kind.PICK_TARGET, 0, 3,
            List.of("Player2", "Player3"), "2 个目标"));
        list.add(new ZjhUiStage("target-6", "比牌选目标（6 人局）", Kind.PICK_TARGET, 0, 6,
            List.of("Player2", "Player3", "Player4", "Player5", "Player6"), "5 个目标：最挤的一档"));
        list.add(new ZjhUiStage("target-longname", "比牌选目标（长 ID）", Kind.PICK_TARGET, 0, 4,
            List.of("NotchTheGreat16", "jeb_the_second", "Dinnerbone_99"), "16 字符 ID：最宽的一档"));

        // ---- 结算：人数决定行数，自己赢/输决定礼花
        for (int n = 2; n <= 6; n++) {
            list.add(settled("settled-win-" + n, n, true));
            list.add(settled("settled-lose-" + n, n, false));
        }

        return List.copyOf(list);
    }
}
