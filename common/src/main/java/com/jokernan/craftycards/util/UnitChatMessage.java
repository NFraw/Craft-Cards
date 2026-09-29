package com.jokernan.craftycards.util;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.entity.Entity;

/**
 * 带单位名称前缀的聊天消息工具。
 * <p>
 * 消息格式：{@code [<单位名称>] <消息内容>}，其中单位名称通过翻译键
 * {@code "unitname.<unitName>"} 获取（支持多语言）。
 * </p>
 *
 * <h3>使用示例</h3>
 * <pre>{@code
 * // 发送格式：[Crafty Cards] 操作成功
 * new UnitChatMessage("mod_name", player)
 *     .printMessage(ChatFormatting.GREEN, Component.translatable("message.success"));
 * }</pre>
 */
public class UnitChatMessage {
    /** 单位名称标识符（对应翻译键 {@code "unitname.xxx"} 的后缀）。 */
    private final String unitName;
    /** 接收消息的玩家实体数组。 */
    private final Entity[] players;

    /**
     * 构造函数。
     *
     * @param unitName 单位名称标识符（如 {@code "mod_name"}、{@code "poker_chip"}）
     * @param players  接收消息的玩家实体
     */
    public UnitChatMessage(String unitName, Entity... players) {
        this.unitName = unitName;
        this.players = players;
    }

    /**
     * 向所有目标玩家发送格式化聊天消息。
     * <p>消息格式：{@code [单位名称] 消息内容}，方括号为白色，消息内容为指定颜色。</p>
     *
     * @param format  消息内容的颜色/样式
     * @param message 消息内容组件
     */
    public void printMessage(ChatFormatting format, MutableComponent message) {
        for (Entity player : players) {
            player.sendSystemMessage(
                Component.literal("[").withStyle(ChatFormatting.WHITE)
                    .append(getUnitName().append("] "))
                    .append(message.withStyle(format))
            );
        }
    }

    /**
     * 获取单位名称的翻译组件。
     * <p>翻译键格式：{@code "unitname.<unitName>"}，在语言文件中定义。</p>
     *
     * @return 翻译后的单位名称组件
     */
    private MutableComponent getUnitName() {
        return Component.translatable("unitname." + unitName);
    }
}
