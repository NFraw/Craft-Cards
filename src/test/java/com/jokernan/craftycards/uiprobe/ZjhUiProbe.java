package com.jokernan.craftycards.uiprobe;

import com.jokernan.craftycards.client.DDZGameHud;
import com.jokernan.craftycards.client.HudOptionBar;
import com.jokernan.craftycards.client.ZJHLayout;
import com.jokernan.craftycards.client.CardGameHudLayout;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 炸金花 HUD 的<b>独立可视化探针</b>：不启动游戏、不连服务器、不需要四个客户端。
 *
 * <p>一次运行做三件事：</p>
 * <ol>
 *   <li><b>布局检查</b>——把每个阶段（等待 / 对局暗牌 / 明牌 1–3 张 / 选牌 / 比牌选目标 / 结算 2–6 人）
 *       在每个分辨率（320×180 起）下的几何量算一遍，逐条判性质：卡牌贴底、卡牌 完整模型比例16:10.2、
 *       竖向四条带不重叠、选项行不越界不重叠、标签完整可见、结算行让开操作行……</li>
 *   <li><b>出图</b>——按真实坐标把每个阶段画成线框图（PNG），布局检查的结论因此可看。</li>
 *   <li><b>出报告</b>——{@code report.md} + {@code index.html}（图廊 + 检查表）。</li>
 * </ol>
 *
 * <p>运行：{@code ./gradlew zjhUiProbe -PmcTarget=1.21.1 --offline}；
 * 或直接 {@code java -cp <test runtime classpath> com.jokernan.craftycards.uiprobe.ZjhUiProbe <输出目录>}。
 * 有 FAIL 时退出码为 1，可以直接挂到 CI 上。</p>
 */
public final class ZjhUiProbe {

    /**
     * 覆盖的分辨率（GUI 缩放后的逻辑像素）。
     * 320×180 = 1280×720 窗口 + GUI 缩放 4，是原版允许的最小档；
     * 960×540 = 1920×1080 + 缩放 2，是最常见的大屏档。
     */
    private static final int[][] VIEWPORTS = {
        {320, 180}, {427, 240}, {640, 360}, {960, 540}
    };

    /** 每个分辨率都出图；320×180 只给"最挤"的几个阶段出图，避免图廊被小图刷满。 */
    private static final List<String> TIGHT_VIEWPORT_STAGES = List.of(
        "playing-dark-r3", "playing-bigblind", "target-6", "target-longname", "settled-win-6", "waiting");

    private record Run(ZjhUiStage stage, int width, int height, List<ZjhUiChecks.Finding> findings) {
        long fails() {
            return findings.stream().filter(f -> !f.ok() && f.severity() == ZjhUiChecks.Severity.ERROR).count();
        }

        long warns() {
            return findings.stream().filter(f -> !f.ok() && f.severity() == ZjhUiChecks.Severity.WARN).count();
        }
    }

    private ZjhUiProbe() {}

    public static void main(String[] args) throws IOException {
        Path out = Path.of(args.length > 0 ? args[0] : "build/zjh-ui-probe").toAbsolutePath();
        Files.createDirectories(out.resolve("shots"));

        List<ZjhUiStage> stages = ZjhUiStage.catalogue();
        List<Run> runs = new ArrayList<>();
        int shots = 0;

        for (int[] vp : VIEWPORTS) {
            int w = vp[0], h = vp[1];
            for (ZjhUiStage stage : stages) {
                List<ZjhUiChecks.Finding> findings = ZjhUiChecks.run(w, h, stage);
                runs.add(new Run(stage, w, h, findings));
                boolean wantShot = h != 180 || TIGHT_VIEWPORT_STAGES.contains(stage.id());
                if (wantShot) {
                    ZjhUiSketch.render(out.resolve("shots").resolve(shotName(stage, w, h)), stage, w, h, findings);
                    shots++;
                }
            }
        }

        long checks = runs.stream().mapToLong(r -> r.findings().size()).sum();
        long fails = runs.stream().mapToLong(Run::fails).sum();
        long warns = runs.stream().mapToLong(Run::warns).sum();

        printConsole(runs, stages.size(), checks, fails, warns);
        Files.writeString(out.resolve("report.md"), markdown(runs, stages, checks, fails, warns), StandardCharsets.UTF_8);
        Files.writeString(out.resolve("index.html"), html(runs, stages, checks, fails, warns), StandardCharsets.UTF_8);

        System.out.println();
        System.out.println("输出目录: " + out);
        System.out.println("  report.md / index.html / shots/*.png (" + shots + " 张)");
        if (fails > 0) System.exit(1);
    }

    private static String shotName(ZjhUiStage stage, int w, int h) {
        return stage.id() + "@" + w + "x" + h + ".png";
    }

    // ---------------------------------------------------------------- 控制台

    private static void printConsole(List<Run> runs, int stageCount, long checks, long fails, long warns) {
        System.out.println("炸金花 HUD 布局探针");
        System.out.println("  阶段 " + stageCount + " × 分辨率 " + VIEWPORTS.length
            + " = " + runs.size() + " 个组合，检查点 " + checks);
        System.out.println();

        // 逐组合一行
        System.out.printf("%-22s %-9s %5s %5s %5s%n", "阶段", "分辨率", "检查", "FAIL", "WARN");
        for (Run r : runs) {
            boolean bad = r.fails() > 0 || r.warns() > 0;
            if (!bad) continue;
            System.out.printf("%-22s %-9s %5d %5d %5d%n",
                r.stage().id(), r.width() + "×" + r.height(), r.findings().size(), r.fails(), r.warns());
        }

        // 失败明细
        if (fails + warns > 0) {
            System.out.println();
            for (Run r : runs) {
                for (ZjhUiChecks.Finding f : r.findings()) {
                    if (!f.ok()) System.out.println("  [" + f.verdict() + "] " + f.stage() + " @" + f.viewport()
                        + " · " + f.rule() + " · " + f.detail());
                }
            }
        }
        System.out.println();
        System.out.println("结果: " + (checks - fails - warns) + " PASS / " + fails + " FAIL / " + warns + " WARN");
    }

    // ---------------------------------------------------------------- 报告

    private static String markdown(List<Run> runs, List<ZjhUiStage> stages, long checks, long fails, long warns) {
        StringBuilder sb = new StringBuilder();
        sb.append("# 炸金花 HUD 布局探针报告\n\n");
        sb.append("生成时间：").append(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))).append("\n\n");
        sb.append("| 项 | 值 |\n|---|---|\n");
        sb.append("| 阶段 | ").append(stages.size()).append(" |\n");
        sb.append("| 分辨率 | ").append(VIEWPORTS.length).append(" |\n");
        sb.append("| 检查点 | ").append(checks).append(" |\n");
        sb.append("| PASS | ").append(checks - fails - warns).append(" |\n");
        sb.append("| FAIL | ").append(fails).append(" |\n");
        sb.append("| WARN | ").append(warns).append(" |\n\n");

        sb.append("## 检查了什么\n\n");
        for (Map.Entry<String, String> e : rules().entrySet()) {
            sb.append("- **").append(e.getKey()).append("** — ").append(e.getValue()).append("\n");
        }
        sb.append('\n');

        sb.append("## 与斗地主的竖向语法对照\n\n");
        sb.append("| 分辨率 | 斗地主选项行 y | 炸金花选项行 y | 差值 | 斗地主项宽 | 炸金花项宽 | 卡牌下沿 |\n");
        sb.append("|---|---|---|---|---|---|---|\n");
        for (String[] row : crossGame()) {
            sb.append("| ").append(String.join(" | ", row)).append(" |\n");
        }
        sb.append("\n提示行、反馈行的位置两边都由 `HudOptionBar.hintY/messageY` 从各自的选项行 y 推出")
            .append("（提示在选项上方 ").append(HudOptionBar.HINT_GAP).append("px、反馈再上方 ")
            .append(HudOptionBar.MESSAGE_GAP).append("px），所以竖向语法一致；")
            .append("两游戏现在都先换算到高度320的设计画布，再应用同一操作锚点。表中坐标、宽度已乘回缩放系数，单位为GUI像素。")
            .append("旧报告直接把GUI高度代入斗地主公式，14/28px差值不是当时的实机差距；本探针通过也不代表实机功能测试通过。\n\n");

        sb.append("## 逐组合结果\n\n");
        sb.append("| 阶段 | 分辨率 | 检查 | FAIL | WARN |\n|---|---|---|---|---|\n");
        for (Run r : runs) {
            sb.append("| ").append(r.stage().id()).append(" | ").append(r.width()).append("×").append(r.height())
                .append(" | ").append(r.findings().size()).append(" | ").append(r.fails())
                .append(" | ").append(r.warns()).append(" |\n");
        }
        sb.append('\n');

        if (fails + warns > 0) {
            sb.append("## 未通过项\n\n");
            for (Run r : runs) {
                for (ZjhUiChecks.Finding f : r.findings()) {
                    if (!f.ok()) {
                        sb.append("- `").append(f.verdict()).append("` **").append(f.rule()).append("** @ ")
                            .append(f.stage()).append(" / ").append(f.viewport()).append(" — ")
                            .append(f.detail()).append('\n');
                    }
                }
            }
            sb.append('\n');
        }
        return sb.toString();
    }

    private static String html(List<Run> runs, List<ZjhUiStage> stages, long checks, long fails, long warns) {
        StringBuilder sb = new StringBuilder();
        sb.append("<!DOCTYPE html><html lang=\"zh-CN\"><head><meta charset=\"utf-8\">")
            .append("<title>炸金花 HUD 布局探针</title><style>")
            .append(":root{color-scheme:dark}")
            .append("body{margin:0;padding:24px 32px;background:#14171c;color:#e6e6e6;")
            .append("font:14px/1.6 -apple-system,'Segoe UI','Microsoft YaHei',sans-serif}")
            .append("h1{font-size:20px;margin:0 0 4px}h2{font-size:15px;margin:28px 0 10px;color:#ffd700}")
            .append(".meta{color:#8b949e;font-size:13px;margin-bottom:18px}")
            .append(".kpi{display:flex;gap:12px;flex-wrap:wrap;margin-bottom:8px}")
            .append(".kpi div{background:#1c2027;border:1px solid #2b313a;border-radius:8px;padding:8px 14px;min-width:88px}")
            .append(".kpi b{display:block;font-size:20px}.ok{color:#57d977}.bad{color:#ff5f56}.warn{color:#ffb62e}")
            .append("table{border-collapse:collapse;width:100%;font-size:13px}")
            .append("th,td{border-bottom:1px solid #2b313a;padding:5px 8px;text-align:left}")
            .append("th{color:#8b949e;font-weight:500}tr:hover td{background:#1a1e24}")
            .append(".grid{display:grid;grid-template-columns:repeat(auto-fill,minmax(330px,1fr));gap:16px}")
            .append("figure{margin:0;background:#1c2027;border:1px solid #2b313a;border-radius:8px;overflow:hidden}")
            .append("figure img{width:100%;display:block;image-rendering:pixelated}")
            .append("figcaption{padding:7px 10px;font-size:12px;color:#b9c0c8;border-top:1px solid #2b313a}")
            .append("figcaption b{color:#e6e6e6}figcaption .bad{color:#ff5f56}")
            .append("code{background:#22272e;padding:1px 5px;border-radius:4px;font-size:12px}")
            .append("</style></head><body>");

        sb.append("<h1>炸金花 HUD 布局探针</h1>");
        sb.append("<div class=\"meta\">")
            .append(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")))
            .append(" · 阶段 ").append(stages.size()).append(" × 分辨率 ").append(VIEWPORTS.length)
            .append(" · 线框图为按真实坐标绘制，非游戏截图</div>");

        sb.append("<div class=\"kpi\">")
            .append(kpi(checks - fails - warns, "PASS", "ok"))
            .append(kpi(fails, "FAIL", fails > 0 ? "bad" : ""))
            .append(kpi(warns, "WARN", warns > 0 ? "warn" : ""))
            .append("</div>");

        // 未通过项
        if (fails + warns > 0) {
            sb.append("<h2>未通过项</h2><table><tr><th>级别</th><th>阶段</th><th>分辨率</th><th>规则</th><th>实测</th></tr>");
            for (Run r : runs) {
                for (ZjhUiChecks.Finding f : r.findings()) {
                    if (f.ok()) continue;
                    String cls = f.severity() == ZjhUiChecks.Severity.ERROR ? "bad" : "warn";
                    sb.append("<tr><td class=\"").append(cls).append("\">").append(f.verdict()).append("</td><td><code>")
                        .append(esc(f.stage())).append("</code></td><td>").append(f.viewport())
                        .append("</td><td>").append(esc(f.rule())).append("</td><td>").append(esc(f.detail()))
                        .append("</td></tr>");
                }
            }
            sb.append("</table>");
        }

        // 与斗地主的竖向语法对照
        sb.append("<h2>与斗地主的竖向语法对照</h2><table><tr><th>分辨率</th><th>斗地主选项行 y</th>")
            .append("<th>炸金花选项行 y</th><th>差值</th><th>斗地主项宽</th><th>炸金花项宽</th><th>卡牌下沿</th></tr>");
        for (String[] row : crossGame()) {
            sb.append("<tr>");
            for (String cell : row) sb.append("<td>").append(esc(cell)).append("</td>");
            sb.append("</tr>");
        }
        sb.append("</table><p class=\"meta\">提示行与反馈行的位置两边都由 <code>HudOptionBar.hintY/messageY</code> ")
            .append("从各自的选项行 y 推出（提示在上 ").append(HudOptionBar.HINT_GAP).append("px、反馈再上 ")
            .append(HudOptionBar.MESSAGE_GAP).append("设计像素）。两游戏共用320高设计画布及操作锚点，表中数值已换算回GUI像素。")
            .append("旧报告的14/28px并非实机差距。布局探针不替代真实客户端功能验收。</p>");

        // 图廊：按阶段分组
        sb.append("<h2>线框图（427/640/960 三档全阶段；320×180 只画最挤的几个）</h2><div class=\"grid\">");
        for (ZjhUiStage stage : stages) {
            for (Run r : runs) {
                if (r.stage() != stage) continue;
                boolean shot = r.height() != 180 || TIGHT_VIEWPORT_STAGES.contains(stage.id());
                if (!shot) continue;
                String name = shotName(stage, r.width(), r.height());
                sb.append("<figure><img src=\"shots/").append(name).append("\" alt=\"").append(esc(name)).append("\">")
                    .append("<figcaption><b>").append(esc(stage.title())).append("</b> · ")
                    .append(r.width()).append("×").append(r.height());
                if (r.fails() > 0) sb.append(" · <span class=\"bad\">FAIL ").append(r.fails()).append("</span>");
                else if (r.warns() > 0) sb.append(" · <span class=\"warn\">WARN ").append(r.warns()).append("</span>");
                sb.append("<br>").append(esc(stage.note())).append("</figcaption></figure>");
            }
        }
        sb.append("</div>");

        // 规则清单
        sb.append("<h2>检查规则</h2><table><tr><th>规则</th><th>含义</th></tr>");
        for (Map.Entry<String, String> e : rules().entrySet()) {
            sb.append("<tr><td><code>").append(esc(e.getKey())).append("</code></td><td>")
                .append(esc(e.getValue())).append("</td></tr>");
        }
        sb.append("</table></body></html>");
        return sb.toString();
    }

    private static String kpi(long value, String label, String cls) {
        return "<div><b class=\"" + cls + "\">" + value + "</b>" + label + "</div>";
    }

    private static String esc(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }

    /** 规则清单：id → 人话解释（报告与图廊共用）。 */
    private static Map<String, String> rules() {
        Map<String, String> map = new LinkedHashMap<>();
        map.put("actions-clear-crosshair", "整条操作行位于准星安全区上方，包含底衬与焦点范围");
        map.put("cards-bottom-anchored", "卡牌下沿恒等于 屏高 − 底边距（默认 30，与斗地主 hudBottomMargin 一致）");
        map.put("card-model-bounds", "按完整模型宽10.2、高16计算牌框及实绘缩放，不把中间牌面宽9当成完整模型宽");
        map.put("cards-do-not-overlap", "三张牌互不重叠（间距 > 牌宽）");
        map.put("cards-inside-viewport", "整片手牌左右各留 ≥ 8px");
        map.put("cards-centered", "三张牌整体水平居中");
        map.put("cards-fit-height", "卡牌上沿 ≥ 60，让开标题/阶段/局势/反馈四行");
        map.put("message-clears-header", "反馈行让开顶部三行（标题/阶段/局势）");
        map.put("message-above-hint", "反馈行在提示行上方");
        map.put("message-gap-shared", "反馈→提示→选项的间距等于 HudOptionBar 的共享常量（两游戏同一套）");
        map.put("hint-above-options", "提示行在选项行上方且不压（斗地主叫分阶段原来是反的，已一并纠正）");
        map.put("options-above-hand-info", "选项行在手牌信息上方且不压");
        map.put("hint-above-hand-info", "无选项行的阶段，提示行直接让开手牌信息");
        map.put("hand-info-above-cards", "手牌信息不压卡牌");
        map.put("option-count", "排布项数与阶段应有项数一致");
        map.put("option-uniform-width", "等宽项：与斗地主叫分选项同一个 HudOptionBar");
        map.put("option-inside-viewport", "选项行含焦点框左右各 2px 都在视口内");
        map.put("option-no-overlap", "相邻选项不重叠");
        map.put("option-min-hit-width", "每项至少 10px 可点");
        map.put("option-label-fully-visible", "每个标签完整显示（不被 plainSubstrByWidth 截断）");
        map.put("settled-header-above-rows", "结算标题/获胜行不压第一行");
        map.put("settled-block-centered", "结算内容块垂直居中（与斗地主同一套），不贴顶留大片空白");
        map.put("settled-rows-clear-hint", "最后一行让开底部那行关闭提示");
        map.put("settled-row-fits-name", "行高 ≥ 14，名字行在行内");
        map.put("settled-text-column-clear", "右侧文字列不压牌列且可用宽 ≥ 40");
        map.put("settled-two-lines-inside-row", "行高 ≥ 22 时两行文字都在行内");
        map.put("settled-one-line-inside-row", "行高 < 22 时退成一行且在行内");
        map.put("settled-tight-net-visible", "一行模式先裁牌型、不裁净得失数字（数字必须留在屏幕上）");
        map.put("settled-hint-inside-viewport", "底部提示行在视口内");
        map.put("settled-title-inside-viewport", "结算标题在视口内");
        return map;
    }

    /**
     * 两个游戏的 HUD 竖向语法对照（同一分辨率下并排看）。
     *
     * <p>斗地主的操作块锚点是固定偏移 {@code DDZGameHud.ACTION_ROW_OFFSET}（17 张牌铺满底部，
     * 位置不随牌数变）；炸金花的锚点由手牌几何往上排（3 张大牌 + 一行手牌信息）。
     * 提示行、反馈行的位置都由同一个 {@link com.jokernan.craftycards.client.HudOptionBar} 规则推出，
     * 所以"提示在选项上方、反馈在提示上方、间距 13/13"两边完全一致；
     * 剩下的差别只有锚点本身，表里直接给出差值。</p>
     */
    private static List<String[]> crossGame() {
        List<String[]> rows = new ArrayList<>();
        for (int[] vp : VIEWPORTS) {
            var canvas=CardGameHudLayout.viewport(vp[0],vp[1]);
            int w = canvas.width(), h = canvas.height();
            int ddzOptions = HudOptionBar.actionRowY(h, DDZGameHud.ACTION_ROW_OFFSET);
            int zjhOptions = ZJHLayout.optionsY(w, h);
            int ddzW = HudOptionBar.solve(w, new int[]{18, 18, 18, 18}, ddzOptions)[0].width();
            int zjhW = HudOptionBar.solve(w, new int[]{18, 28, 28, 28, 18, 28}, zjhOptions)[0].width();
            rows.add(new String[]{
                vp[0] + "×" + vp[1],
                guiPixels(ddzOptions,canvas.scale()), guiPixels(zjhOptions,canvas.scale()), guiPixels(zjhOptions-ddzOptions,canvas.scale()),
                guiPixels(ddzW,canvas.scale()),guiPixels(zjhW,canvas.scale()),
                guiPixels(h-CardGameHudLayout.bottomMargin(ZJHLayout.DEFAULT_BOTTOM_MARGIN,canvas.scale()),canvas.scale())
            });
        }
        return rows;
    }
    private static String guiPixels(int value,float scale){return String.format(java.util.Locale.ROOT,"%.1f",value*scale);}
}
