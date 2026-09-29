package com.jokernan.craftycards.util;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.entity.Entity;

/**
 * 聊天消息工具类 — 以模组名称为前缀发送格式化聊天消息。
 * <p>
 * 消息格式：{@code [模组名] 消息内容}，用于向玩家显示模组相关的提示信息。
 * </p>
 */
public class ChatHelper {
    /**
     * 向指定玩家发送带模组名称前缀的格式化聊天消息。
     * <p>
     * 消息格式：{@code [Crafty Cards] <消息内容>}，
     * 其中 {@code [Crafty Cards]} 的翻译键为 {@code "unitname.mod_name"}。
     * </p>
     *
     * @param format    文本颜色/样式（如 {@link ChatFormatting#GREEN}、{@link ChatFormatting#RED}）
     * @param component 消息内容组件
     * @param players   接收消息的玩家实体（可多个）
     */
    public static void printModMessage(ChatFormatting format, MutableComponent component, Entity... players) {
        UnitChatMessage unitMessage = new UnitChatMessage("mod_name", players);
        unitMessage.printMessage(format, component);
    }
}
