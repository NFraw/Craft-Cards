package com.jokernan.craftycards.client;

import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * 渲染参数页（客户端）：把最常调的几项做成档位按钮，其余仍在 {@code visual.json} 里改。
 *
 * <p>为什么只放开这几项：{@code visual.json} 有近 40 个参数，大多是排布细节
 * （扇形角、叠放步进、拱形高度…），随手调只会把画面调坏。这里挑的是"玩家一看就知道
 * 自己想要什么"的几项——手牌多大、立牌离人多远多高、出牌多大、筹码多高。
 * 其余项照旧改文件，再点本页的「重载配置」让它生效（等价于 {@code /craftycards reload}）。</p>
 */
public class RenderConfigScreen extends ConfigScreenBase {
    private static final Integer[] HAND_CARD_W = {28, 34, 40, 48, 56, 64};
    private static final Integer[] HAND_GAP = {24, 32, 40, 50, 62};
    private static final Integer[] HAND_FAN = {0, 10, 20, 30};
    private static final Float[] HAND_DIST = {0.4F, 0.6F, 0.8F, 1.0F, 1.2F, 1.5F};
    private static final Float[] HAND_HEIGHT = {0.8F, 1.0F, 1.2F, 1.5F, 1.8F};
    private static final Float[] PLAYED_SCALE = {0.15F, 0.20F, 0.25F, 0.32F, 0.40F};
    private static final Float[] CHIP_HEIGHT = {0.1F, 0.2F, 0.3F, 0.5F, 0.8F};

    public RenderConfigScreen(Screen parent) {
        super(parent, "渲染参数", "只需调最常用的几项，其余在 visual.json 里改");
    }

    @Override
    protected void buildRows() {
        list.header("HUD 手牌");
        list.row(choiceRow("手牌大小（像素宽）", "太大可能盖住画面下半部分",
            HAND_CARD_W, () -> RenderConfig.hudCardW, v -> RenderConfig.hudCardW = v, String::valueOf));
        list.row(choiceRow("手牌间距", "牌少时的间距；牌多时会自动压缩",
            HAND_GAP, () -> RenderConfig.hudHandGap, v -> RenderConfig.hudHandGap = v, String::valueOf));
        list.row(choiceRow("扇形角度", "两端倾斜角度，0 = 平铺一排",
            HAND_FAN, () -> Math.round(RenderConfig.hudFanAngle), v -> RenderConfig.hudFanAngle = v,
            v -> v + "°"));
        list.row(toggleRow("显示「上轮出牌」HUD 块", "手牌右上角那块小牌行（桌面出牌已可视化，默认关）",
            () -> RenderConfig.hudPlayedShow, v -> RenderConfig.hudPlayedShow = v));

        list.header("世界内他人持牌");
        list.row(choiceRow("距离（格）", "牌组立在持牌人前方多远",
            HAND_DIST, () -> RenderConfig.handForwardDist, v -> RenderConfig.handForwardDist = v,
            v -> v + " 格"));
        list.row(choiceRow("高度（格）", "牌组离持牌人脚底多高",
            HAND_HEIGHT, () -> RenderConfig.handHeight, v -> RenderConfig.handHeight = v,
            v -> v + " 格"));

        list.header("世界内出牌与筹码");
        list.row(choiceRow("出牌缩放", "桌面上的出牌大小",
            PLAYED_SCALE, () -> RenderConfig.playedCardScale, v -> RenderConfig.playedCardScale = v,
            String::valueOf));
        list.row(choiceRow("筹码悬浮高度（格）", "桌面上那个转动的筹码图标离桌面多高",
            CHIP_HEIGHT, () -> RenderConfig.chipDisplayHeight, v -> RenderConfig.chipDisplayHeight = v,
            v -> v + " 格"));

        list.header("其它");
        list.row(customRow("重载配置", "重新读 visual.json（等价于 /craftycards reload）",
            Button.builder(Component.literal("重载"), b -> {
                RenderConfig.load();
                rebuild();
                status = "已重载 visual.json（未保存的界面改动被文件内容覆盖）";
            }).size(56, 20).build()));
        list.note("文件：" + RenderConfig.file());
        list.note("其余参数（叠放步进、拱形高度、渲染距离等）直接改文件后点「重载」");
    }

    @Override
    protected void buildFooter() {
        // 渲染参数改动是纯客户端内存值，保存只是写回文件；不需要资源重载
        addFooterButton("保存并生效", () -> {
            RenderConfig.persist();
            status = "已写入 visual.json 并生效";
        });
    }

    @Override
    protected String hint() {
        return "档位按钮改动立即生效；「保存并生效」把当前值写回 visual.json";
    }
}
