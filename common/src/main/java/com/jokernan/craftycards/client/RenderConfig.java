package com.jokernan.craftycards.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.jokernan.craftycards.CCReference;
import com.jokernan.craftycards.platform.GamePaths;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 渲染参数配置（客户端）：把三个渲染类里的硬编码可视化常量收进 JSON 配置文件，
 * 启动时加载一次，游戏内可用 {@code /craftycards reload} 命令重新读取，实现"改参不重启"。
 *
 * <p>配置文件位置：{@code config/crafty_cards/visual.json}（NeoForge 配置目录）。
 * 所有字段都是 {@code public static} 可变字段，渲染代码每帧读取当前值，
 * 因此修改 JSON 并执行 reload 命令后，下一帧立即生效。</p>
 *
 * <p>字段分组对应：
 * <ul>
 *   <li>{@code hud*} — {@link DDZGameHud}（HUD 手牌、结算小牌行）</li>
 *   <li>{@code hand*} — {@link WorldHandCards}（其他玩家面前的牌背立牌）</li>
 *   <li>{@code played*} — {@link WorldPlayedCards}（牌桌中央的出牌扇形）</li>
 * </ul></p>
 *
 * <p>JSON 读写通过内部 {@link Data} 载体完成——Gson 不序列化 static 字段，
 * 故用同名实例字段的 DTO 中转。文件缺失时写默认值；读取失败回退默认值，不影响游戏。</p>
 */
public class RenderConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    // === HUD 手牌（DDZGameHud） ===
    public static int hudCardW = 40;           // 手牌像素宽度
    public static int hudHandGap = 40;         // 手牌间距（牌少时）
    public static int hudMinGap = 18;          // 手牌最小间距（牌多时压缩）
    public static float hudMaxWidthRatio = 0.9F;  // 手牌最大占屏宽比
    public static int hudSelectLift = 14;      // 选中牌上移像素
    public static int hudBottomMargin = 30;    // 手牌距屏幕底部边距
    public static int hudArchPixels = 20;      // 手牌拱形最大偏移（中间最高，两端下沉）
    public static float hudFanAngle = 20F;     // 手牌扇形两端最大倾斜角度（度）
    public static int hudSmallCardW = 22;      // 结算/底牌小牌行卡宽
    public static int hudSmallCardGap = 25;    // 小牌行默认间距
    public static boolean hudPlayedShow = false; // 是否显示"上轮出牌"HUD 块（手牌右上角）
    public static int hudPlayedCardW = 30;     // 上轮出牌牌宽（手牌右上角那块）
    public static int hudPlayedCardGap = 12;   // 上轮出牌牌间距
    public static int hudPlayedMinGap = 6;     // 上轮出牌最小间距（牌多时压缩，允许轻微叠压）
    public static int hudPlayedOffsetX = 12;   // 上轮出牌相对手牌右缘再往右的偏移
    public static int hudPlayedOffsetY = 18;   // 上轮出牌底边相对手牌顶再往上的偏移（须大于选中上移量）

    // === 世界内他人持牌（WorldHandCards，跟随各玩家位置渲染） ===
    public static float handForwardDist = 0.8F; // 牌组在持牌人前方多远（方块，沿其视线水平方向）
    public static float handHeight = 1.2F;      // 牌组离持牌人脚底的高度（方块，约胸口）
    public static float handMaxRenderDist = 32F; // 超出这个距离（方块）不渲染他人持牌，避免隔墙看到远处飘牌
    public static float handCardScale = 0.4F;   // 立牌缩放
    public static float handCardGap = 0.12F;    // 立牌间距
    public static float handFanAngle = 1.0F;    // 立牌扇形（度/张）：中间正对持牌人、两端外翻。
                                               // 前移量按 DDZGeom.fanDepthAdvance 消掉了扇形引起的左右不对称，可放心调大
    public static float handTiltX = -10.0F;     // 立牌前后倾倒角度（度，绕 X 轴，正值向玩家方向倾，默认向后倾）
    public static float handTiltZ = 0F;         // 立牌左右倾斜角度（度，绕 Z 轴，正值右侧下沉）
    public static float handArchHeight = 0.12F; // 立牌拱形高度（方块）：中间牌最高、两端回到基准高度
    public static float handStackStep = 0.020F;  // 立牌相邻两张的**平面距离**（方块，= 沿牌面法线的前移量）：
                                               // 不随扇形变化（扇形只改横向角度），左右两支重叠观感一致；
                                               // 低于牌模型厚度（0.5/16 格 × 手牌缩放）时自动抬到刚好不穿插的下限

    // === 世界内出牌（WorldPlayedCards） ===
    public static float playedCardScale = 0.25F; // 出牌缩放
    public static float playedCardGap = 0.07F;   // 出牌横排间距
    public static float playedOffset = 0.35F;    // 出牌组偏向出牌者座位的距离
    public static float playedMaxYaw = 30.0F;    // 出牌扇形两端最大旋转角度（度）

    // === 桌面悬浮的筹码图标（WorldTableChip） ===
    public static float chipDisplayHeight = 0.30F;  // 图标离桌面的高度（方块）
    public static float chipDisplayScale = 0.7F;    // 图标缩放
    public static float chipSpinDegPerTick = 1.2F;  // 自转速度（度/刻，约 24 度/秒）
    public static float chipBobAmplitude = 0.04F;   // 上下浮动幅度（方块，0 = 不浮动）
    public static double chipDisplayMaxDist = 48.0; // 超出这个距离不渲染（方块）
    public static float playedSurfaceLift = 0.010F; // 出牌整组离桌面的高度（方块）：与桌面顶面错开，避免共面
    public static float playedStackStep = 0.005F;   // 出牌逐张叠放的高度步进（方块）：牌之间错开，显出厚度

    private RenderConfig() {}

    /** 配置文件路径（界面显示用）。 */
    public static Path file() {
        return GamePaths.config("visual.json");
    }

    /** 把当前内存中的参数写回配置文件（游戏内改参后调用）。渲染代码每帧读静态字段，无需重载。 */
    public static void persist() {
        save();
    }

    /** 卡牌渲染比例：由 {@link #hudCardW} 推导（补偿 FaceBakery 烘焙与 card_base.json 宽 10.2 的换算）。 */
    public static float hudCardScale() {
        return hudCardW * 16F / 10.2F;
    }

    /** 卡牌渲染高度（像素）：由 {@link #hudCardScale()} 四舍五入。 */
    public static int hudCardH() {
        return Math.round(hudCardScale());
    }

    /** 小牌行（底牌 / 结算 / 上轮出牌）的渲染高度（像素）：同一套 16/10.2 换算。 */
    public static int hudSmallCardH() {
        return Math.round(hudSmallCardW * 16F / 10.2F);
    }

    /** 从配置文件加载渲染参数；文件不存在时写出默认值。任何读取出错都回退默认值。 */
    public static void load() {
        Data data = new Data();
        try {
            if (Files.exists(file())) {
                data = GSON.fromJson(Files.readString(file()), Data.class);
            }
        } catch (Exception e) {
            CCReference.LOG.warn("读取渲染配置失败，使用默认值: {}", e.toString());
        }
        // 空文件/内容为字面 null 时 Gson 返回 null 而不抛异常，需回退默认值，避免 apply 空指针
        if (data == null) data = new Data();
        apply(data);
        save();
        CCReference.LOG.info("Crafty Cards 渲染配置已加载: {}", file());
    }

    private static void apply(Data d) {
        hudCardW = d.hudCardW;
        hudHandGap = d.hudHandGap;
        hudMinGap = d.hudMinGap;
        hudMaxWidthRatio = d.hudMaxWidthRatio;
        hudSelectLift = d.hudSelectLift;
        hudBottomMargin = d.hudBottomMargin;
        hudArchPixels = d.hudArchPixels;
        hudFanAngle = d.hudFanAngle;
        hudSmallCardW = d.hudSmallCardW;
        hudSmallCardGap = d.hudSmallCardGap;
        hudPlayedShow = d.hudPlayedShow;
        hudPlayedCardW = d.hudPlayedCardW;
        hudPlayedCardGap = d.hudPlayedCardGap;
        hudPlayedMinGap = d.hudPlayedMinGap;
        hudPlayedOffsetX = d.hudPlayedOffsetX;
        hudPlayedOffsetY = d.hudPlayedOffsetY;
        handForwardDist = d.handForwardDist;
        handHeight = d.handHeight;
        handMaxRenderDist = d.handMaxRenderDist;
        handCardScale = d.handCardScale;
        handCardGap = d.handCardGap;
        handFanAngle = d.handFanAngle;
        handTiltX = d.handTiltX;
        handTiltZ = d.handTiltZ;
        handArchHeight = d.handArchHeight;
        handStackStep = d.handStackStep;
        playedCardScale = d.playedCardScale;
        playedCardGap = d.playedCardGap;
        playedOffset = d.playedOffset;
        playedMaxYaw = d.playedMaxYaw;
        chipDisplayHeight = d.chipDisplayHeight;
        chipDisplayScale = d.chipDisplayScale;
        chipSpinDegPerTick = d.chipSpinDegPerTick;
        chipBobAmplitude = d.chipBobAmplitude;
        chipDisplayMaxDist = d.chipDisplayMaxDist;
        playedSurfaceLift = d.playedSurfaceLift;
        playedStackStep = d.playedStackStep;
    }

    private static Data snapshot() {
        Data d = new Data();
        d.hudCardW = hudCardW;
        d.hudHandGap = hudHandGap;
        d.hudMinGap = hudMinGap;
        d.hudMaxWidthRatio = hudMaxWidthRatio;
        d.hudSelectLift = hudSelectLift;
        d.hudBottomMargin = hudBottomMargin;
        d.hudArchPixels = hudArchPixels;
        d.hudFanAngle = hudFanAngle;
        d.hudSmallCardW = hudSmallCardW;
        d.hudSmallCardGap = hudSmallCardGap;
        d.hudPlayedShow = hudPlayedShow;
        d.hudPlayedCardW = hudPlayedCardW;
        d.hudPlayedCardGap = hudPlayedCardGap;
        d.hudPlayedMinGap = hudPlayedMinGap;
        d.hudPlayedOffsetX = hudPlayedOffsetX;
        d.hudPlayedOffsetY = hudPlayedOffsetY;
        d.handCardScale = handCardScale;
        d.handCardGap = handCardGap;
        d.handFanAngle = handFanAngle;
        d.handTiltX = handTiltX;
        d.handTiltZ = handTiltZ;
        d.handArchHeight = handArchHeight;
        d.handStackStep = handStackStep;
        d.playedCardScale = playedCardScale;
        d.playedCardGap = playedCardGap;
        d.playedOffset = playedOffset;
        d.playedMaxYaw = playedMaxYaw;
        d.chipDisplayHeight = chipDisplayHeight;
        d.chipDisplayScale = chipDisplayScale;
        d.chipSpinDegPerTick = chipSpinDegPerTick;
        d.chipBobAmplitude = chipBobAmplitude;
        d.chipDisplayMaxDist = chipDisplayMaxDist;
        d.playedSurfaceLift = playedSurfaceLift;
        d.playedStackStep = playedStackStep;
        return d;
    }

    private static void save() {
        try {
            Files.createDirectories(file().getParent());
            Files.writeString(file(), GSON.toJson(snapshot()));
        } catch (IOException e) {
            CCReference.LOG.warn("保存渲染配置失败: {}", e.toString());
        }
    }

    /** Gson 序列化载体：使用实例字段（Gson 不处理 static 字段），缺省值与默认一致。 */
    private static class Data {
        public int hudCardW = 40;
        public int hudHandGap = 40;
        public int hudMinGap = 18;
        public float hudMaxWidthRatio = 0.9F;
        public int hudSelectLift = 14;
        public int hudBottomMargin = 30;
        public int hudArchPixels = 20;
        public float hudFanAngle = 20F;
        public int hudSmallCardW = 22;
        public int hudSmallCardGap = 25;
        public boolean hudPlayedShow = false;
        public int hudPlayedCardW = 30;
        public int hudPlayedCardGap = 12;
        public int hudPlayedMinGap = 6;
        public int hudPlayedOffsetX = 12;
        public int hudPlayedOffsetY = 18;
        public float handForwardDist = 0.8F;
        public float handHeight = 1.2F;
        public float handMaxRenderDist = 32F;
        public float handCardScale = 0.4F;
        public float handCardGap = 0.12F;
        public float handFanAngle = 1.0F;
        public float handTiltX = -10.0F;
        public float handTiltZ = 0F;
        public float handArchHeight = 0.12F;
        public float handStackStep = 0.020F;
        public float playedCardScale = 0.25F;
        public float playedCardGap = 0.07F;
        public float playedOffset = 0.35F;
        public float playedMaxYaw = 30.0F;
        public float chipDisplayHeight = 0.30F;
        public float chipDisplayScale = 0.7F;
        public float chipSpinDegPerTick = 1.2F;
        public float chipBobAmplitude = 0.04F;
        public double chipDisplayMaxDist = 48.0;
        public float playedSurfaceLift = 0.010F;
        public float playedStackStep = 0.005F;
    }
}
