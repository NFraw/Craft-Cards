package com.jokernan.craftycards;

import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Crafty Cards 模组全局常量与工具方法。
 * <p>
 * 集中管理模组 ID、日志器和资源定位器，供全项目引用。
 * 模组 ID 为 {@code "crafty_cards"}，必须与 {@code gradle.properties} 中的 {@code mod_id}
 * 以及 {@code neoforge.mods.toml} 中的 {@code modId} 保持一致，否则游戏无法识别本模组。
 * </p>
 */
public class CCReference {
    /** 模组唯一标识符，用于注册表命名空间、资源路径和网络包类型。 */
    public static final String MOD_ID = "crafty_cards";

    /** 模组显示名称，同时作为日志器名称（SLF4J logger name）。 */
    public static final String MOD_NAME = "Crafty Cards";

    /** 全局日志器，输出到 {@code run/logs/} 下的日志文件。 */
    public static final Logger LOG = LoggerFactory.getLogger(MOD_NAME);

    /**
     * 构建以本模组命名空间为前缀的 {@link ResourceLocation}。
     * <p>
     * 等价于 {@code ResourceLocation.fromNamespaceAndPath("crafty_cards", path)}，
     * 用于方块/物品/实体注册名、贴图路径、网络包类型标识等。
     * </p>
     *
     * @param path 资源路径（如 {@code "ddz_table"}、{@code "textures/item/card_ace_spades.png"}）
     * @return 形如 {@code crafty_cards:<path>} 的 ResourceLocation
     */
    public static ResourceLocation location(String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }
}
