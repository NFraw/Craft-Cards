package com.jokernan.craftycards.client;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CardGameHudLayoutTest {
    @Test void actionOptionsClearTheWorldCrosshair() {
        int row=HudOptionBar.actionRowY(320,CardGameHudLayout.ACTION_OFFSET);
        assertTrue(row+HudOptionBar.ITEM_HEIGHT<320/2-7,"操作行应完整位于准星安全区上方");
    }
    @Test void bothGamesKeepTheSameOnScreenAnchorAcrossGuiScales() {
        for(int[] size:new int[][]{{320,180},{427,240},{480,320},{640,360},{960,540}}) {
            var shared=CardGameHudLayout.viewport(size[0],size[1]);
            var ddz=HudHandMetrics.viewport(size[0],size[1]);
            assertEquals(ddz.width(),shared.width());assertEquals(ddz.height(),shared.height());
            assertEquals(ddz.scale(),shared.scale());
            assertEquals(HudOptionBar.actionRowY(ddz.height(),DDZGameHud.ACTION_ROW_OFFSET),
                ZJHLayout.optionsY(shared.width(),shared.height()));
            assertEquals(140/320F,ZJHLayout.optionsY(shared.width(),shared.height())*shared.scale()/size[1],0.001F);
        }
    }
    @Test void settlementFooterStaysAboveVanillaBottomUiOnTheDesignCanvas() {
        var ddz=HudSettlementLayout.solve(320,9,35,2);
        assertEquals(CardGameHudLayout.closeY(320),ddz.closeY);
        assertEquals(ddz.closeY,ZJHLayout.resultHintY(320));
        assertTrue(ddz.closeY+9<=320-40);
    }
    @Test void handAlwaysReservesTheActualGuiHotbarHeight() {
        for(int height:new int[]{180,240,320,360,540}) {
            var viewport=CardGameHudLayout.viewport(height*16/9,height);
            assertTrue(CardGameHudLayout.bottomMargin(30,viewport.scale())*viewport.scale()>=26);
        }
    }
}
