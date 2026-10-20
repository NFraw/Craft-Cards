package com.jokernan.craftycards.game;

import org.junit.jupiter.api.Test;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

class DDZTableLayoutTest {
    record Cell(int x, int z) {}

    @Test void everyTileOfTwoByThreeResolvesToSameTable() {
        for (int x = -4; x < -2; x++) for (int z = 7; z < 10; z++) {
            var layout = DDZTableLayout.discover(x, z,
                (a, b) -> a >= -4 && a < -2 && b >= 7 && b < 10);
            assertEquals(new DDZTableLayout(-4, 7, 2, 3), layout);
            assertEquals(-3, layout.centerX());
            assertEquals(8.5, layout.centerZ());
        }
    }

    @Test void rotatedRectangleAndSingleBlockWork() {
        assertEquals(new DDZTableLayout(0, 0, 3, 2),
            DDZTableLayout.discover(2, 1, (x, z) -> x >= 0 && x < 3 && z >= 0 && z < 2));
        assertEquals(new DDZTableLayout(2, 8, 1, 1),
            DDZTableLayout.discover(2, 8, (x, z) -> x == 2 && z == 8));
    }

    @Test void rejectsLShapeHoleMissingTableAndOversizedComponent() {
        Set<Cell> l = Set.of(new Cell(0, 0), new Cell(1, 0), new Cell(0, 1));
        assertThrows(IllegalArgumentException.class,
            () -> DDZTableLayout.discover(0, 0, (x, z) -> l.contains(new Cell(x, z))));
        assertThrows(IllegalArgumentException.class,
            () -> DDZTableLayout.discover(0, 0,
                (x, z) -> x >= 0 && x < 3 && z >= 0 && z < 3 && !(x == 1 && z == 1)));
        assertThrows(IllegalArgumentException.class,
            () -> DDZTableLayout.discover(0, 0, (x, z) -> false));
        assertThrows(IllegalArgumentException.class,
            () -> DDZTableLayout.discover(0, 0, (x, z) -> true));
    }

    @Test void diagonalNeighboursDoNotMerge() {
        assertEquals(new DDZTableLayout(0, 0, 1, 1), DDZTableLayout.discover(0, 0,
            (x, z) -> (x == 0 && z == 0) || (x == 1 && z == 1)));
    }

    @Test void nineBlockRectanglesAreAcceptedFromEveryCorner() {
        for (int[] size : new int[][]{{3, 3}, {1, 9}, {9, 1}}) {
            for (int x : new int[]{0, size[0]-1}) for (int z : new int[]{0, size[1]-1}) {
                assertEquals(new DDZTableLayout(0, 0, size[0], size[1]),
                    DDZTableLayout.discover(x, z, (a, b) -> a >= 0 && a < size[0] && b >= 0 && b < size[1]));
            }
        }
    }

    @Test void tenthBlockAndOversizedBridgeAreRejectedButTemporaryLIsAllowed() {
        assertThrows(IllegalArgumentException.class, () -> DDZTableLayout.discover(0, 0,
            (x, z) -> x >= 0 && x < 2 && z >= 0 && z < 5));
        assertFalse(DDZTableLayout.canAdd(3, 0, (x, z) -> x >= 0 && x < 3 && z >= 0 && z < 3));
        assertFalse(DDZTableLayout.canAdd(5, 0, (x, z) -> z == 0 && x >= 0 && x <= 10 && x != 5));
        assertTrue(DDZTableLayout.canAdd(0, 1, (x, z) -> z == 0 && (x == 0 || x == 1)));
        assertTrue(DDZTableLayout.canAdd(2, 2, (x, z) -> x >= 0 && x < 3 && z >= 0 && z < 3 && !(x == 2 && z == 2)));
        assertThrows(IllegalArgumentException.class, () -> new DDZTableLayout(0, 0, 2, 5));
    }

    @Test void distanceAndChangeDetectionUseWholeTable() {
        var layout = new DDZTableLayout(0, 0, 2, 3);
        assertEquals(1, layout.distanceSquared(3, 1, 2.5, 0));
        assertEquals(0, layout.distanceSquared(1, 0.5, 1, 0));
        assertTrue(layout.contains(1, 2));
        assertTrue(layout.touches(-1, 1));
        assertTrue(layout.touches(1, 3));
        assertFalse(layout.touches(-1, -1));
        assertFalse(layout.touches(0, 4));
    }
}
