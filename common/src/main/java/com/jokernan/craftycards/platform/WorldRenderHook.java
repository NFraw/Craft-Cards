package com.jokernan.craftycards.platform;

import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.culling.Frustum;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 世界渲染接缝：**"半透明方块画完之后"这一个时刻**由各加载器提供，画什么由 common 决定。
 *
 * <p>三个世界渲染器（他人身前立牌 / 桌面出牌展示 / 桌面筹码）原先都挂在 NeoForge 的
 * {@code RenderLevelStageEvent(AFTER_TRANSLUCENT_BLOCKS)} 上。Fabric 没有等价的渲染阶段事件，
 * 只能 mixin —— 但**注入点不是猜的**：NeoForge 自己就是在原版
 * {@code LevelRenderer.renderSectionLayer(...)} 的末尾按渲染类型分发的
 * （源码里那行 {@code ClientHooks.dispatchRenderStage(...)}），Fabric 侧照着同一个点注入
 * （`@At("RETURN")` + 只认 {@code RenderType.translucent()}），两边语义一致。</p>
 *
 * <p>上下文只带渲染器真正用到的东西：{@link Camera}、{@link Frustum}、渲染刻与
 * {@link DeltaTracker}。三个渲染器都是自己 {@code new PoseStack()} 再按相机位置平移，
 * 所以不需要把世界的 pose stack 传进来。</p>
 */
public final class WorldRenderHook {
    /**
     * 渲染上下文（把各加载器事件对象里的取值统一成原版类型）。
     *
     * @param camera      当前相机（渲染器要用它的位置把世界坐标转成相对坐标）
     * @param frustum     视锥（渲染器用它做可见性剔除）
     * @param renderTick  渲染刻（动画用）
     * @param partialTick 帧间插值（动画用，与 {@code renderTick} 相加得到连续时间）
     */
    public record Context(Camera camera, Frustum frustum, int renderTick, DeltaTracker partialTick) {}

    /** 一个渲染器的回调。 */
    public interface Sink {
        /** 在半透明方块绘制完成后调用。 */
        void afterTranslucentBlocks(Context ctx);
    }

    /**
     * 已注册的渲染器。
     *
     * <p>用写时复制列表：注册发生在客户端初始化（单线程），而这里每帧都会读，
     * 不想为"启动期注册"引入锁。</p>
     */
    private static final List<Sink> SINKS = new CopyOnWriteArrayList<>();

    private WorldRenderHook() {}

    /** 由各加载器的客户端初始化调用（顺序即绘制顺序）。 */
    public static void register(Sink sink) {
        if (sink != null) {
            SINKS.add(sink);
        }
    }

    /** 由各加载器在"半透明方块之后"那一点调用；没有注册任何渲染器时是空转。 */
    public static void afterTranslucentBlocks(Context ctx) {
        if (SINKS.isEmpty()) return;
        for (Sink sink : SINKS) {
            sink.afterTranslucentBlocks(ctx);
        }
    }
}
