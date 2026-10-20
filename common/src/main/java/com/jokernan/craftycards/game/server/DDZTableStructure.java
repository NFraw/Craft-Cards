package com.jokernan.craftycards.game.server;

import com.jokernan.craftycards.block.BlockDDZTable;
import com.jokernan.craftycards.blockentity.DDZTableBlockEntity;
import com.jokernan.craftycards.game.DDZTableLayout;
import com.jokernan.craftycards.network.payload.TableKey;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

/** Server-authoritative identity and geometry; never loads a chunk to search for a table. */
public record DDZTableStructure(TableKey key, DDZTableLayout layout) {
    public static DDZTableStructure resolve(Level level, BlockPos clicked) {
        if (!level.hasChunkAt(clicked) || !(level.getBlockState(clicked).getBlock() instanceof BlockDDZTable))
            throw new IllegalArgumentException("这里没有已加载的斗地主牌桌");
        var block = level.getBlockState(clicked).getBlock();
        var layout = DDZTableLayout.discover(clicked.getX(), clicked.getZ(), (x, z) -> {
            var pos = new BlockPos(x, clicked.getY(), z);
            if (!level.hasChunkAt(pos))
                throw new IllegalArgumentException("牌桌周围区块尚未加载，请靠近后重试");
            // Different games/registered table blocks never join, even if they share a Java class.
            return level.getBlockState(pos).is(block);
        });
        return new DDZTableStructure(new TableKey(level.dimension(),
            new BlockPos(layout.minX(), clicked.getY(), layout.minZ())), layout);
    }

    public boolean contains(TableKey tile) {
        return tile.dimension().equals(key.dimension()) && tile.pos().getY() == key.pos().getY()
            && layout.contains(tile.pos().getX(), tile.pos().getZ());
    }

    public boolean touches(Level level, BlockPos pos) {
        return level.dimension().equals(key.dimension()) && pos.getY() == key.pos().getY()
            && layout.touches(pos.getX(), pos.getZ());
    }

    public boolean intact(Level level) {
        try { return equals(resolve(level, key.pos())); }
        catch (IllegalArgumentException ignored) { return false; }
    }

    /** Northwest existing configuration wins; an unconfigured new corner inherits an older owner. */
    public DDZTableBlockEntity prepareConfig(Level level) {
        if (!(level.getBlockEntity(key.pos()) instanceof DDZTableBlockEntity root))
            throw new IllegalArgumentException("牌桌数据尚未就绪，请重新放置牌桌");
        if (!configured(root)) {
            outer: for (int x = layout.minX(); x < layout.minX() + layout.width(); x++) {
                for (int z = layout.minZ(); z < layout.minZ() + layout.depth(); z++) {
                    if (level.getBlockEntity(new BlockPos(x, key.pos().getY(), z)) instanceof DDZTableBlockEntity old
                        && configured(old)) {
                        root.copyConfigFrom(old);
                        break outer;
                    }
                }
            }
        }
        root.ensureSeeded();
        replicateConfig(level, root);
        return root;
    }

    private static boolean configured(DDZTableBlockEntity table) {
        return table.initialized() || !table.chipItem().isEmpty();
    }

    /** Back up configuration on each member so expanding/removing the anchor does not lose it. */
    public void replicateConfig(Level level, DDZTableBlockEntity root) {
        for (int x = layout.minX(); x < layout.minX() + layout.width(); x++) {
            for (int z = layout.minZ(); z < layout.minZ() + layout.depth(); z++) {
                BlockPos pos = new BlockPos(x, key.pos().getY(), z);
                if (level.hasChunkAt(pos) && level.getBlockEntity(pos) instanceof DDZTableBlockEntity member)
                    member.copyConfigFrom(root);
            }
        }
    }
}
