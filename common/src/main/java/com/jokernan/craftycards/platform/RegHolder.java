package com.jokernan.craftycards.platform;

import java.util.function.Supplier;

/**
 * 一个"由加载器注册出来的对象"的把手（方块 / 物品 / 实体类型 / 方块实体类型 / 音效事件 …）。
 *
 * <p>为什么需要它：common 层要能在**不知道加载器是谁**的前提下拿到
 * {@code crafty_cards:ddz_table} 这个方块、{@code crafty_cards:card} 这件物品。
 * 两个加载器的**注册方式完全不同**（NeoForge 要 {@code DeferredRegister} + 模组事件总线；
 * Fabric 是模组初始化时直接 {@code Registry.register}），但"注册出来的东西"是同一个游戏对象。
 * 于是 common 只声明 <b>id + 构造逻辑</b>（{@link #create}），**谁去登记**由各加载器负责
 * （{@link #bind} / {@link #bindValue}）。</p>
 *
 * <p>两种绑定方式分别对应两个加载器：
 * NeoForge 把 {@code DeferredHolder} 直接 bind 进来（它本身就是 Supplier，注册完成前取值
 * 会抛它自己的异常）；Fabric 在 {@code Registry.register} 之后把返回值 {@link #bindValue} 进来。</p>
 *
 * <p>取不到就抛：内容没注册是**程序错误**（两个加载器的初始化阶段都会注册完），
 * 而"静默返回 null"只会把它变成之后某处更难定位的 NPE。</p>
 */
public final class RegHolder<T> implements Supplier<T> {
    /** 注册路径（不带命名空间），用于报错信息。 */
    private final String path;
    /** 绑定进来的取值方式；null = 还没绑定。 */
    private volatile Supplier<T> supplier;

    private RegHolder(String path) {
        this.path = path;
    }

    /** 声明一个待注册的内容（在各加载器共用的 {@code Init*} 类里调用）。 */
    public static <T> RegHolder<T> create(String path) {
        return new RegHolder<>(path);
    }

    /** 注册路径（不带命名空间）。 */
    public String path() {
        return path;
    }

    /** 绑定一个取值方式（NeoForge：绑 {@code DeferredHolder}）。 */
    public void bind(Supplier<T> supplier) {
        this.supplier = supplier;
    }

    /** 绑定一个已经拿到的对象（Fabric：{@code Registry.register} 的返回值）。 */
    public void bindValue(T value) {
        this.supplier = () -> value;
    }

    /** 是否已经绑定（加载器自检用，例如"漏注册某个内容"的启动检查）。 */
    public boolean isBound() {
        return supplier != null;
    }

    @Override
    public T get() {
        Supplier<T> s = supplier;
        if (s == null) {
            throw new IllegalStateException(
                "crafty_cards:" + path + " 还没注册——加载器必须在初始化时绑定它");
        }
        return s.get();
    }

    @Override
    public String toString() {
        return "RegHolder[" + path + (isBound() ? ", 已绑定]" : ", 未绑定]");
    }
}
