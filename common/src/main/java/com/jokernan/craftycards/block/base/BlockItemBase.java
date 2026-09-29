package com.jokernan.craftycards.block.base;

import net.minecraft.world.item.BlockItem;
import net.minecraft.world.level.block.Block;

/**
 * 方块物品基类 — 将方块包装为物品，使玩家能在创造模式物品栏中获取并放置方块。
 * <p>
 * 使用默认物品属性（{@code new Properties()}），最大堆叠 64。
 * 如需特殊属性（如不可堆叠、附魔光效），可在子类中覆盖构造函数。
 * </p>
 */
public class BlockItemBase extends BlockItem {
    /**
     * 构造函数。
     *
     * @param block 对应的方块实例
     */
    public BlockItemBase(Block block) {
        super(block, new Properties());
    }
}
