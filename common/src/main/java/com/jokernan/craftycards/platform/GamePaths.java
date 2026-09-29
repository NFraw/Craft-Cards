package com.jokernan.craftycards.platform;

import java.nio.file.Path;

/**
 * 游戏目录里的路径（配置目录）——<b>各加载器必须告诉我们的少数几件事之一</b>。
 *
 * <p>原先这几处直接写 {@code FMLPaths.CONFIGDIR.get()}（NeoForge 专有）。多加载器下这一行
 * 就得由各自的入口尽早注入，所以抽成这里的一个可设置值。</p>
 *
 * <p><b>为什么是"惰性取值"而不是 {@code static final Path FILE = config("x.json")}：</b>
 * 静态字段在**类初始化时**求值，而"加载器注入配置目录"与"某个配置类第一次被加载"的先后
 * 顺序并不由我们控制——一旦配置类先加载，字段就把兜底值缓存下来了（表现为"改了 config
 * 目录却不生效"，极难排查）。所以 {@link #config(String)} 每次现算，这些调用点也都改成
 * 方法调用。</p>
 *
 * <p>兜底值取相对路径 {@code config}：游戏的工作目录就是游戏目录（开发与正式环境都成立），
 * 所以即使加载器没来得及注入也不会指到别处去。</p>
 */
public final class GamePaths {
    /** 配置根目录；由各加载器入口调用 {@link #setConfigDir} 覆盖兜底值。 */
    private static volatile Path configDir = Path.of("config");

    private GamePaths() {}

    /**
     * 注入配置根目录（NeoForge: {@code FMLPaths.CONFIGDIR.get()}；
     * Fabric: {@code FabricLoader.getInstance().getConfigDir()}）。
     *
     * <p>入口里尽早调用；{@code null} 会被忽略（保持兜底值，不至于把路径搞成 NPE）。</p>
     */
    public static void setConfigDir(Path dir) {
        if (dir != null) {
            configDir = dir;
        }
    }

    /** 本模组的配置目录：{@code <config>/crafty_cards}。 */
    public static Path modConfigDir() {
        return configDir.resolve("crafty_cards");
    }

    /** 本模组配置目录下的一个文件或子目录，例如 {@code config("server.json")}。 */
    public static Path config(String child) {
        return modConfigDir().resolve(child);
    }
}
