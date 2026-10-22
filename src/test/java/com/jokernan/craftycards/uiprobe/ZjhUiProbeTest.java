package com.jokernan.craftycards.uiprobe;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 把探针的检查引擎挂到普通单测上：{@code ./gradlew test} 就会跑完所有阶段 × 分辨率。
 *
 * <p>探针（{@link ZjhUiProbe}）负责出图出报告、给人看；这个测试负责在 CI 上<b>挡住</b>回退。
 * 两者共用 {@link ZjhUiChecks}，所以报告里说的"通过"和测试说的"通过"是同一件事。</p>
 */
class ZjhUiProbeTest {

    /** 与探针同一套分辨率；320×180 是原版允许的最小 GUI 尺寸。 */
    private static final int[][] VIEWPORTS = {{320, 180}, {427, 240}, {640, 360}, {960, 540}};

    @Test
    void everyStagePassesEveryLayoutCheckOnEveryViewport() {
        List<String> failures = new ArrayList<>();
        for (int[] vp : VIEWPORTS) {
            for (ZjhUiStage stage : ZjhUiStage.catalogue()) {
                for (ZjhUiChecks.Finding f : ZjhUiChecks.run(vp[0], vp[1], stage)) {
                    if (!f.ok() && f.severity() == ZjhUiChecks.Severity.ERROR) {
                        failures.add(f.stage() + " @" + f.viewport() + " · " + f.rule() + " · " + f.detail());
                    }
                }
            }
        }
        assertTrue(failures.isEmpty(), () -> "布局检查未通过：\n  " + String.join("\n  ", failures));
    }

    /**
     * 阶段目录必须与行动策略同源：这里的每一项都应能从 {@code ZJHControls.buttons}
     * 或 {@code ZJHClient.choices()} 的规则推出来。策略改了而目录没跟上，这条会红。
     */
    @Test
    void stageCatalogueMatchesTheActionPolicy() {
        ZjhUiStage darkR3 = find("playing-dark-r3");
        assertTrue(darkR3.options().contains("明牌"), "未翻牌时应有明牌");
        assertTrue(darkR3.options().contains("比牌 2"), "暗牌比牌按 2 倍明注：" + darkR3.options());
        assertEquals(6, darkR3.options().size(), "暗牌可比牌是最宽的 6 项");

        ZjhUiStage full = find("playing-revealed3");
        assertFalse(full.options().contains("明牌"), "三张全开后应撤掉明牌");
        assertTrue(full.options().contains("跟注 2"), "明牌后费用翻倍：" + full.options());
        assertTrue(full.options().contains("比牌 4"), "明牌后比牌按 4 倍明注：" + full.options());

        // 等待开局：加入即就绪，唯一的选项是离开（准备/开始按钮已经没有了）
        assertEquals(List.of("离开牌局"), find("waiting").options());

        // 结算：服务端已自动退局，没有选项行，只有底部一行关闭提示
        assertFalse(find("settled-win-3").hasOptionRow(), "结算页不该再有选项行");

        assertFalse(find("selecting-cards").hasOptionRow(), "选牌模式没有选项行");
    }

    /** 目录本身不能有空壳：id 唯一、数量够、除"本来就没有选项"的阶段外都得有出口。 */
    @Test
    void catalogueIsNonEmptyAndIdsAreUnique() {
        List<ZjhUiStage> stages = ZjhUiStage.catalogue();
        assertTrue(stages.size() >= 15, "阶段覆盖太少：" + stages.size());
        assertEquals(stages.size(), stages.stream().map(ZjhUiStage::id).distinct().count(), "阶段 id 不能重复");
        for (ZjhUiStage stage : stages) {
            boolean optionless = stage.kind() == ZjhUiStage.Kind.SELECT_CARDS
                || stage.kind() == ZjhUiStage.Kind.SETTLED;
            if (!optionless) {
                assertFalse(stage.options().isEmpty(), stage.id() + " 必须有选项（否则玩家没有出口）");
            }
        }
    }

    private static ZjhUiStage find(String id) {
        return ZjhUiStage.catalogue().stream().filter(s -> s.id().equals(id)).findFirst()
            .orElseThrow(() -> new AssertionError("阶段目录里没有 " + id));
    }
}
