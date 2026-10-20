package com.jokernan.craftycards.game;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Set;
import java.util.function.BiPredicate;

/** Pure geometry of a coplanar rectangular table, in world X/Z block coordinates. */
public record DDZTableLayout(int minX, int minZ, int width, int depth) {
    public static final int MAX_BLOCKS = 9;
    public static final int MAX_SIDE = MAX_BLOCKS;
    private static final int[][] NEIGHBOURS = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};

    public DDZTableLayout {
        if (width < 1 || depth < 1 || width > MAX_SIDE || depth > MAX_SIDE || width * depth > MAX_BLOCKS)
            throw new IllegalArgumentException("一张牌桌最多只能由 9 个方块组成");
    }

    private record Cell(int x, int z) {}

    private static Set<Cell> component(int x, int z, BiPredicate<Integer, Integer> isTable) {
        if (!isTable.test(x, z)) throw new IllegalArgumentException("这里没有斗地主牌桌");
        var pending = new ArrayDeque<Cell>();
        var visited = new HashSet<Cell>();
        pending.add(new Cell(x, z));
        while (!pending.isEmpty()) {
            Cell cell = pending.removeFirst();
            if (!visited.add(cell)) continue;
            if (visited.size() > MAX_BLOCKS)
                throw new IllegalArgumentException("一张牌桌最多只能由 9 个方块组成，请分开摆放");
            for (int[] d : NEIGHBOURS) {
                Cell next = new Cell(cell.x + d[0], cell.z + d[1]);
                if (!visited.contains(next) && isTable.test(next.x, next.z)) pending.addLast(next);
            }
        }
        return visited;
    }

    /** Placement must allow temporary L shapes, but never a tenth tile or an oversized bridge. */
    public static boolean canAdd(int x, int z, BiPredicate<Integer, Integer> isTable) {
        try {
            component(x, z, (a, b) -> (a == x && b == z) || isTable.test(a, b));
            return true;
        } catch (IllegalArgumentException ignored) { return false; }
    }

    public static DDZTableLayout discover(int x, int z, BiPredicate<Integer, Integer> isTable) {
        Set<Cell> visited = component(x, z, isTable);
        int minX = x, maxX = x, minZ = z, maxZ = z;
        for (Cell cell : visited) {
            minX = Math.min(minX, cell.x); maxX = Math.max(maxX, cell.x);
            minZ = Math.min(minZ, cell.z); maxZ = Math.max(maxZ, cell.z);
        }
        int width = maxX - minX + 1, depth = maxZ - minZ + 1;
        if (visited.size() != width * depth)
            throw new IllegalArgumentException("请把相连的牌桌补成完整矩形后再使用");
        return new DDZTableLayout(minX, minZ, width, depth);
    }

    public double centerX() { return minX + width / 2.0; }
    public double centerZ() { return minZ + depth / 2.0; }

    public boolean contains(int x, int z) {
        return x >= minX && x < minX + width && z >= minZ && z < minZ + depth;
    }

    public boolean touches(int x, int z) {
        return contains(x, z) || contains(x - 1, z) || contains(x + 1, z)
            || contains(x, z - 1) || contains(x, z + 1);
    }

    /** Distance to the closest point of the whole table, rather than its anchor corner. */
    public double distanceSquared(double x, double y, double z, int baseY) {
        double dx = Math.max(minX - x, Math.max(0, x - minX - width));
        double dy = Math.max(baseY - y, Math.max(0, y - baseY - 1));
        double dz = Math.max(minZ - z, Math.max(0, z - minZ - depth));
        return dx * dx + dy * dy + dz * dz;
    }
}
