package com.jokernan.craftycards.block.base;

import net.minecraft.world.level.block.Block;

/**
 * 模组方块基类 — 所有自定义方块的公共父类。
 * <p>
 * 当前仅提供简单的构造函数委托，预留扩展点供子类统一添加行为
 * （如通用的方块状态更新、粒子效果等）。
 * </p>
 */
public class BlockBase extends Block {
    /**
     * 构造函数。
     *
     * @param properties 方块属性（材质、硬度、声音、碰撞箱等）
     */
    public BlockBase(Properties properties) {
        super(properties);
    }
}
