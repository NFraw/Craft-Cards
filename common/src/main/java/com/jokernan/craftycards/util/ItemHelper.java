package com.jokernan.craftycards.util;

import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;

/**
 * 物品工具类 — 提供物品 NBT 数据读写和物品实体生成功能。
 * <p>
 * 在 Minecraft 1.21+ 中，物品自定义数据存储在 {@link DataComponents#CUSTOM_DATA} 组件中，
 * 不再直接使用 {@code stack.getTag()}。本工具类封装了新 API 的读写操作。
 * </p>
 *
 * <h3>主要功能</h3>
 * <ul>
 *   <li>NBT 读写：{@link #getNBT}、{@link #setNBT}、{@link #getOrCreateNBT}</li>
 *   <li>物品生成：{@link #spawnStackAtEntity} 在实体位置生成掉落物</li>
 * </ul>
 */
public class ItemHelper {

    /**
     * 获取物品的自定义 NBT 数据（只读副本）。
     * <p>如果物品没有自定义数据，返回空的 {@link CompoundTag}（不会修改物品）。</p>
     *
     * @param stack 目标物品堆
     * @return NBT 数据副本
     */
    public static CompoundTag getNBT(ItemStack stack) {
        CustomData customData = stack.get(DataComponents.CUSTOM_DATA);
        if (customData != null) {
            return customData.copyTag();
        }
        return new CompoundTag();
    }

    /**
     * 设置物品的自定义 NBT 数据（覆盖写入）。
     *
     * @param stack 目标物品堆
     * @param tag   要写入的 NBT 数据
     */
    public static void setNBT(ItemStack stack, CompoundTag tag) {
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
    }

    /**
     * 获取或创建物品的自定义 NBT 数据（可修改副本）。
     * <p>
     * 如果物品已有自定义数据，返回其副本；否则创建新的空 NBT 并写入物品。
     * 返回的 {@link CompoundTag} 可直接修改，修改后需调用 {@link #setNBT} 写回。
     * </p>
     *
     * @param stack 目标物品堆
     * @return 可修改的 NBT 数据副本（已写入物品）
     */
    public static CompoundTag getOrCreateNBT(ItemStack stack) {
        CustomData customData = stack.get(DataComponents.CUSTOM_DATA);
        CompoundTag tag = customData != null ? customData.copyTag() : new CompoundTag();
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        return tag;
    }

    /**
     * 在实体位置生成掉落物实体（无拾取延迟、无初始速度）。
     * <p>用于出牌、发牌、筹码交易等场景。</p>
     *
     * @param world  世界
     * @param entity 目标实体（掉落物生成位置）
     * @param stack  要生成的物品堆
     */
    public static void spawnStackAtEntity(Level world, Entity entity, ItemStack stack) {
        spawnStack(world, entity.position().x, entity.position().y, entity.position().z, stack);
    }

    /**
     * 在指定坐标生成掉落物实体。
     * <p>设置无拾取延迟（{@code setNoPickUpDelay}）和零速度，使物品立即可拾取且不弹飞。</p>
     */
    private static void spawnStack(Level world, double x, double y, double z, ItemStack stack) {
        ItemEntity item = new ItemEntity(world, x, y, z, stack);
        item.setNoPickUpDelay();
        item.setDeltaMovement(0, 0, 0);
        world.addFreshEntity(item);
    }
}
