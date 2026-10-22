package com.jokernan.craftycards.game;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ChipDenominationsTest {
    @Test void supportsOnlyMatchingMaterialFamilies() {
        assertEquals("minecraft:iron_block",ChipDenominations.blockFor("minecraft:iron_ingot"));
        assertEquals("minecraft:gold_block",ChipDenominations.blockFor("minecraft:gold_ingot"));
        assertEquals("minecraft:diamond_block",ChipDenominations.blockFor("minecraft:diamond"));
        assertNull(ChipDenominations.blockFor("minecraft:emerald"));
        assertNull(ChipDenominations.blockFor("minecraft:iron_block"));
    }
    @Test void breaksOneBlockAndReturnsExactChange() {
        var p=ChipDenominations.withdraw(0,1,1);
        assertEquals(0,p.units());assertEquals(1,p.blocks());assertEquals(8,p.change());
        p=ChipDenominations.withdraw(0,5,40);
        assertEquals(5,p.blocks());assertEquals(5,p.change());
    }
    @Test void spendsLooseUnitsFirstAndDoesNotBreakBlocksUnnecessarily() {
        var p=ChipDenominations.withdraw(5,2,4);
        assertEquals(4,p.units());assertEquals(0,p.blocks());assertEquals(0,p.change());
        p=ChipDenominations.withdraw(5,2,14);
        assertEquals(5,p.units());assertEquals(1,p.blocks());assertEquals(0,p.change());
    }
    @Test void rejectsInsufficientAndNegativeRequests() {
        assertThrows(IllegalArgumentException.class,()->ChipDenominations.withdraw(0,1,10));
        assertThrows(IllegalArgumentException.class,()->ChipDenominations.withdraw(1,0,-1));
        assertThrows(IllegalArgumentException.class,()->ChipDenominations.withdraw(-1,0,0));
    }
    @Test void everyAffordableWithdrawalConservesValue() {
        for(int loose=0;loose<=20;loose++)for(int blocks=0;blocks<=20;blocks++)
            for(int requested=0;requested<=loose+blocks*9;requested++) {
                var p=ChipDenominations.withdraw(loose,blocks,requested);
                assertEquals(requested,p.units()+p.blocks()*9-p.change());
                assertTrue(p.units()<=loose&&p.blocks()<=blocks&&p.change()>=0&&p.change()<9);
            }
    }
}
