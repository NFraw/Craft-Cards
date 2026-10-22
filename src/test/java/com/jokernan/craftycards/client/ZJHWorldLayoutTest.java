package com.jokernan.craftycards.client;

import com.jokernan.craftycards.game.DDZGeom;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ZJHWorldLayoutTest {
    @Test void overlappingCardsHaveMoreNormalSeparationThanTheirThickness() {
        for(float scale:new float[]{.2f,.36f,.6f})for(float fan:new float[]{0,3,8}) {
            var slots=ZJHWorldLayout.cards(scale,.104f,.001f,fan);
            for(int i=0;i<2;i++) {
                double angle=Math.toRadians(slots[i].angle());
                double separation=(slots[i+1].x()-slots[i].x())*Math.sin(angle)
                    +(slots[i+1].z()-slots[i].z())*Math.cos(angle);
                assertTrue(separation>scale/32f,"overlapping cards must never be coplanar");
            }
        }
    }
    @Test void metadataIsRetainedForEliminatedPlayersAndNeverIncludesPrivateCards() {
        var text=ZJHWorldLayout.labels(false,12,"比牌失败",true,true);
        assertEquals("出局",text.center());assertEquals("累计投入 12",text.left());
        assertEquals("比牌失败",text.right());assertEquals("庄",text.dealer());assertEquals("",text.turn());
        var active=ZJHWorldLayout.labels(true,8,"跟注 4",false,true);
        assertEquals("行动中",active.turn());
    }
}
