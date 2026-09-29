package com.jokernan.craftycards.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.ContainerObjectSelectionList;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.DoubleConsumer;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * 配置界面的通用骨架：<b>标题 + 面包屑 + 可滚动设置行 + 底部按钮</b>。
 *
 * <p>存在的理由：原先三块配置（音频、玩法、渲染）挤在同一层——音频界面底部一行塞了
 * 7 个控件（音量、开关、音乐包、重载、扫描、保存、完成），小窗口下按钮直接叠在一起；
 * 玩法入口还挂在音频界面的底部，让人以为玩法属于音频。现在改成"主页 → 分类页"的多层级结构，
 * 每个页面只放同一类的设置行，行控件右对齐、可滚动，底部固定只留「保存」与「返回」。</p>
 *
 * <p>子类只需要实现 {@link #buildRows()}，用 {@link #header} / {@link #choiceRow} /
 * {@link #toggleRow} / {@link #sliderRow} / {@link #customRow} / {@link #note} 往列表里加行。</p>
 */
public abstract class ConfigScreenBase extends Screen {
    /** 循环按钮 / 开关的默认宽度。 */
    protected static final int CONTROL_WIDTH = 92;
    /** 滑块宽度。 */
    protected static final int SLIDER_WIDTH = 132;
    /** 行高：与 {@link ConfigLayout} 同源，改一处即可。 */
    protected static final int ROW_HEIGHT = ConfigLayout.ROW_HEIGHT;

    /** 上层界面：按 ESC / 点「返回」回到这里。 */
    protected final Screen parent;
    /** 面包屑（显示在标题下方，表明当前位置）。 */
    protected final String breadcrumb;
    /** 底部状态文字（保存结果、错误提示），非空时覆盖 {@link #hint()}。 */
    protected String status = "";
    protected ConfigList list;
    private final List<Button> footerButtons = new ArrayList<>();

    protected ConfigScreenBase(Screen parent, String title, String breadcrumb) {
        super(Component.literal(title));
        this.parent = parent;
        this.breadcrumb = breadcrumb;
    }

    /** Current labels and control values, also used by the in-game acceptance client. */
    public List<ConfigRow> configRows() {
        return list == null ? List.of() : List.copyOf(list.children());
    }

    public void scrollToLastRow() {
        if (list != null) list.setScrollAmount(Double.MAX_VALUE);
    }

    @Override
    protected void init() {
        footerButtons.clear();
        // 注意 ContainerObjectSelectionList 的构造参数是 (宽, 高, 顶边, 行高)：第三个是 y、第二个是高度，
        // 列表纵向占 y .. y+height，故这里传"可用高度"而不是底边坐标（排版算法见 ConfigLayout，有单测）
        list = new ConfigList(minecraft, width, ConfigLayout.listHeight(height),
            ConfigLayout.LIST_TOP, ROW_HEIGHT);
        addRenderableWidget(list);
        buildRows();
        buildFooter();
        // 返回永远在最后（最右），子类按钮依次排在它左边
        addFooterButton(returnLabel(), this::onClose);
        layoutFooter();
    }

    /** 子类往 {@link #list} 里加设置行。 */
    protected abstract void buildRows();

    /** 子类可覆盖，用 {@link #addFooterButton} 添加自己的底部按钮（如「保存并应用」）。 */
    protected void buildFooter() {}

    /** 底部按钮的文案：子页是「返回」，主页是「完成」（它关掉的是整个设置界面）。 */
    protected String returnLabel() {
        return "返回";
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        guiGraphics.drawCenteredString(font, title, width / 2, 8, 0xFFFFFFFF);
        guiGraphics.drawCenteredString(font, clip(breadcrumb), width / 2, 20, 0xFFAAAAAA);
        String bottom = status.isEmpty() ? hint() : status;
        if (!bottom.isEmpty()) {
            guiGraphics.drawCenteredString(font, clip(bottom), width / 2,
                ConfigLayout.statusY(height), status.isEmpty() ? hintColor() : 0xFF55FF55);
        }
    }

    /** 底部默认提示（status 为空时显示）。 */
    protected String hint() {
        return "";
    }

    /**
     * 默认提示的颜色，默认灰色；需要提醒玩家注意时（未保存、声道静音等）覆盖成橙色。
     *
     * <p>不用 {@code §} 颜色码：文字要按宽度截断，而截断用的
     * {@code plainSubstrByWidth} 会把格式码一并剥掉，颜色会静默失效。</p>
     */
    protected int hintColor() {
        return 0xFF808080;
    }

    /** 重建整个列表：行内容依赖编辑状态（某格现在是"待保存"还是"已生效"）时，改完要刷新。 */
    protected void rebuild() {
        if (list == null) return;
        list.clear();
        buildRows();
    }

    @Override
    public void onClose() {
        if (minecraft != null) minecraft.setScreen(parent);
    }

    /**
     * 超长文字按可用宽度截断。
     *
     * <p>状态与提示里会带上文件路径、音乐包名这类长度不可控的文本，
     * 小窗口下直接绘制会跑出屏幕或盖住按钮。</p>
     */
    protected String clip(String text) {
        return clip(text, width - 20);
    }

    /** 按指定像素宽度截断。 */
    protected String clip(String text, int maxWidth) {
        if (text == null || text.isEmpty()) return "";
        return maxWidth <= 0 ? "" : font.plainSubstrByWidth(text, maxWidth);
    }

    // === 底部按钮 ===

    /** 加一个底部按钮（宽度 100，居中排布）。 */
    protected Button addFooterButton(String label, Runnable action) {
        return addFooterButton(label, 100, action);
    }

    protected Button addFooterButton(String label, int buttonWidth, Runnable action) {
        Button button = Button.builder(Component.literal(label), b -> action.run())
            .size(buttonWidth, 20).build();
        footerButtons.add(button);
        addRenderableWidget(button);
        return button;
    }

    /** 把底部按钮居中排成一行。 */
    private void layoutFooter() {
        int[] widths = new int[footerButtons.size()];
        for (int i = 0; i < widths.length; i++) widths[i] = footerButtons.get(i).getWidth();
        int x = ConfigLayout.footerButtonsX(width, widths);
        int y = ConfigLayout.buttonY(height);
        for (Button b : footerButtons) {
            b.setPosition(x, y);
            x += b.getWidth() + ConfigLayout.CONTROL_GAP;
        }
    }

    // === 行工厂 ===

    /** 分组标题行。 */
    protected ConfigRow header(String text) {
        return new ConfigRow(this, text, () -> "", List.of(), 0xFF55AAFF, true);
    }

    /** 纯说明行（灰色小字，无控件）。 */
    protected ConfigRow note(String text) {
        return new ConfigRow(this, text, () -> "", List.of(), 0xFF9A9A9A, false);
    }

    /**
     * 档位行：点按钮在给定档位之间循环。
     *
     * <p>配置值都是有限的小范围枚举，用循环按钮而不是输入框——不可能输错、也不需要校验。
     * 当前值不在档位表里（手改过配置文件）时，下一次点击回到第一档。</p>
     */
    protected <T> ConfigRow choiceRow(String label, String hint, T[] values,
                                      Supplier<T> get, Consumer<T> set, Function<T, String> fmt) {
        Button button = Button.builder(Component.literal(fmt.apply(get.get())), b -> {
            T current = get.get();
            int idx = -1;
            for (int i = 0; i < values.length; i++) {
                if (values[i].equals(current)) {
                    idx = i;
                    break;
                }
            }
            T next = values[(idx + 1) % values.length];
            set.accept(next);
            b.setMessage(Component.literal(fmt.apply(next)));
            status = "";
        }).size(CONTROL_WIDTH, 20).build();
        return customRow(label, hint, button);
    }

    /** 开关行（按钮显示「开 / 关」）。 */
    protected ConfigRow toggleRow(String label, String hint, Supplier<Boolean> get, Consumer<Boolean> set) {
        Button button = Button.builder(Component.literal(get.get() ? "开" : "关"), b -> {
            boolean next = !get.get();
            set.accept(next);
            b.setMessage(Component.literal(next ? "开" : "关"));
            status = "";
        }).size(CONTROL_WIDTH, 20).build();
        return customRow(label, hint, button);
    }

    /** 百分比滑块行（0~1 连续取值）。 */
    protected ConfigRow percentRow(String label, String hint, Supplier<Float> get, Consumer<Float> set) {
        return sliderRow(label, hint, 0.0, 1.0, get.get().doubleValue(), v -> set.accept((float) v),
            v -> Math.round(v * 100) + "%");
    }

    /** 滑块行（连续取值，宽度固定）。 */
    protected ConfigRow sliderRow(String label, String hint, double min, double max,
                                  double value, DoubleConsumer onChange, Function<Double, String> fmt) {
        return customRow(label, hint, new ValueSlider(min, max, value, fmt, onChange));
    }

    /** 自定义控件行（比如"文件名按钮 + 试听"）。 */
    protected ConfigRow customRow(String label, String hint, AbstractWidget... controls) {
        return customRow(label, () -> hint, controls);
    }

    /**
     * 自定义控件行，提示文字每次绘制时现取。
     *
     * <p>用在"控件一改、提示就要跟着变"的行上：音频槽位的来源（内置 / 自配文件 / 待保存）
     * 就是在点文件名按钮的那一刻变的，写死成字符串会一直显示旧值。</p>
     */
    protected ConfigRow customRow(String label, Supplier<String> hint, AbstractWidget... controls) {
        return new ConfigRow(this, label, hint, List.of(controls), 0xFFFFFFFF, false);
    }

    // === 列表与行 ===

    /** 设置行列表（可滚动；分组标题也是一种行）。 */
    protected class ConfigList extends ContainerObjectSelectionList<ConfigRow> {
        ConfigList(Minecraft mc, int width, int height, int y, int itemHeight) {
            super(mc, width, height, y, itemHeight);
        }

        /** 加一行分组标题。 */
        void header(String text) {
            addEntry(ConfigScreenBase.this.header(text));
        }

        /** 加一行说明文字。 */
        void note(String text) {
            addEntry(ConfigScreenBase.this.note(text));
        }

        void row(ConfigRow row) {
            addEntry(row);
        }

        /** 清空所有行（行内容依赖编辑状态，改了要重建）。 */
        void clear() {
            clearEntries();
        }

        @Override
        public int getRowWidth() {
            return width - 40;
        }
    }

    /**
     * 一行设置：左侧标签 + 可选提示小字，右侧一排控件（右对齐、按加入顺序从左到右排）。
     *
     * <p>静态嵌套类但持有宿主界面引用：绘制要用 {@code font} 与按宽度截断（{@link #clip}），
     * 而静态类拿不到宿主实例，所以显式传进来。做成静态是为了能在工厂方法里直接 {@code new}。</p>
     */
    public static class ConfigRow extends ContainerObjectSelectionList.Entry<ConfigRow> {
        private final ConfigScreenBase screen;
        private final String label;
        private final Supplier<String> hint;
        private final List<AbstractWidget> controls;
        private final int color;
        private final boolean headerRow;

        private ConfigRow(ConfigScreenBase screen, String label, Supplier<String> hint,
                          List<AbstractWidget> controls, int color, boolean headerRow) {
            this.screen = screen;
            this.label = label;
            this.hint = hint;
            this.controls = controls;
            this.color = color;
            this.headerRow = headerRow;
        }

        public String labelText() { return label; }

        public List<String> controlTexts() {
            return controls.stream().map(widget -> widget.getMessage().getString()).toList();
        }

        @Override
        public void render(GuiGraphics guiGraphics, int index, int y, int x, int width, int height,
                           int mouseX, int mouseY, boolean hovering, float partialTick) {
            int textY = headerRow ? y + 8 : y + 3;
            // 文字只在"控件左边"这点宽度里排，否则长标签会盖到按钮上（小窗口下尤其明显）
            int textRoom = ConfigLayout.textRoom(width, controlsTotalWidth());
            guiGraphics.drawString(screen.font, screen.clip(label, textRoom), x + 4, textY, color);
            String hintText = hint.get();
            if (hintText != null && !hintText.isEmpty()) {
                guiGraphics.drawString(screen.font, screen.clip(hintText, textRoom), x + 4, y + 15, 0xFF707070);
            }
            if (controls.isEmpty()) return;
            int controlX = ConfigLayout.controlsX(x, width, controlsTotalWidth());
            for (AbstractWidget w : controls) {
                w.setPosition(controlX, y + (height - w.getHeight()) / 2);
                w.render(guiGraphics, mouseX, mouseY, partialTick);
                controlX += w.getWidth() + ConfigLayout.CONTROL_GAP;
            }
        }

        /** 本行所有控件（含间距）的总宽度。 */
        private int controlsTotalWidth() {
            if (controls.isEmpty()) return 0;
            int[] widths = new int[controls.size()];
            for (int i = 0; i < widths.length; i++) widths[i] = controls.get(i).getWidth();
            return ConfigLayout.controlsWidth(widths);
        }

        @Override
        public List<? extends GuiEventListener> children() {
            return controls;
        }

        @Override
        public List<? extends NarratableEntry> narratables() {
            return controls;
        }
    }

    /** 连续取值的滑块（显示由调用方决定，如百分比）。 */
    protected static class ValueSlider extends AbstractSliderButton {
        private final double min;
        private final double max;
        private final Function<Double, String> fmt;
        private final DoubleConsumer onChange;

        ValueSlider(double min, double max, double value, Function<Double, String> fmt, DoubleConsumer onChange) {
            super(0, 0, SLIDER_WIDTH, 20, Component.empty(), clamp01((value - min) / (max - min)));
            this.min = min;
            this.max = max;
            this.fmt = fmt;
            this.onChange = onChange;
            updateMessage();
        }

        private static double clamp01(double v) {
            return Math.max(0.0, Math.min(1.0, v));
        }

        /** 当前实际取值（把 0~1 的滑块位置换算回值域）。 */
        double actual() {
            return min + value * (max - min);
        }

        @Override
        protected void updateMessage() {
            // 父类构造器里就会调到这里，此时 fmt 还是 null
            setMessage(fmt == null ? Component.empty() : Component.literal(fmt.apply(actual())));
        }

        @Override
        protected void applyValue() {
            if (onChange != null) onChange.accept(actual());
        }
    }
}
