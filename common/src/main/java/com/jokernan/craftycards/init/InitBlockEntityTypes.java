package com.jokernan.craftycards.init;

import com.jokernan.craftycards.blockentity.DDZTableBlockEntity;
import com.jokernan.craftycards.platform.RegHolder;
import net.minecraft.world.level.block.entity.BlockEntityType;

/**
 * 方块实体类型注册中心。
 *
 * <p>登记在加载器侧（NeoForge 的 {@code init/NeoForgeBlockEntityTypes}、Fabric 的对应类），
 * 构造逻辑共用这里的一份。当前只有斗地主牌桌自己的方块实体
 * （每桌筹码配置与房主，见 {@link DDZTableBlockEntity}）。</p>
 */
public final class InitBlockEntityTypes {
    /**
     * 斗地主牌桌的方块实体：存<b>这一张桌</b>的筹码配置（类型/底注/门槛/开关）与房主。
     *
     * <p>放在方块实体而不是全局配置里，为的是让「一张桌子一套筹码信息」随区块持久化：
     * 服务器管理员的总闸门（{@code server.json} 的 {@code chipsRequired}）只决定「能不能用」，
     * 关掉再打开不会重置各桌自己的数据。</p>
     */
    public static final RegHolder<BlockEntityType<DDZTableBlockEntity>> DDZ_TABLE = RegHolder.create("ddz_table");

    private InitBlockEntityTypes() {}

    /**
     * 构造牌桌的方块实体类型（两个加载器共用）。
     *
     * <p>这里要取方块实例：NeoForge 侧因此必须让方块先于方块实体类型注册，且本方法在
     * 延迟 lambda 里被调用（注册完成前取 {@code DeferredHolder} 会抛）。</p>
     */
    public static BlockEntityType<DDZTableBlockEntity> createDdzTable() {
        return BlockEntityType.Builder
            .of(DDZTableBlockEntity::new, InitItems.DDZ_TABLE.get())
            .build(null);
    }
}
