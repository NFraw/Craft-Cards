package com.jokernan.craftycards.client;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ZJHControlsTest {
    @Test void revealCanBeRepeatedThroughTheButtonUntilAllSlotsAreShown() {
        var selection=new ZJHControls.Selection();int revealedMask=0;
        for(int count=0;count<3;count++) {
            var buttons=ZJHControls.buttons(count,1,1);
            assertTrue(has(ZJHControls.Kind.REVEAL,buttons));
            assertEquals(count==0?1:2,find(ZJHControls.Kind.CALL,buttons).cost());
            int focus=selection.enterCards(revealedMask);
            assertEquals(count,focus,"再次进入时焦点应落在未翻牌上");
            assertTrue(selection.cards());assertEquals(0,selection.mask());
            selection.toggle(focus,revealedMask);revealedMask|=selection.mask();
            selection.confirmedReveal();
            assertFalse(selection.cards(),"翻牌确认后应返回行动按钮");assertEquals(0,selection.mask());
        }
        assertFalse(has(ZJHControls.Kind.REVEAL,ZJHControls.buttons(3,1,1)));
        assertEquals(-1,selection.enterCards(revealedMask));assertFalse(selection.cards());
    }
    private static boolean has(ZJHControls.Kind kind,java.util.List<ZJHControls.Button> buttons) {
        return buttons.stream().anyMatch(b->b.kind()==kind);
    }
    private static ZJHControls.Button find(ZJHControls.Kind kind,java.util.List<ZJHControls.Button> buttons) {
        return buttons.stream().filter(b->b.kind()==kind).findFirst().orElseThrow();
    }

    /** 用户实机反馈：明牌只出现一次。入口必须留到三张全翻开为止。 */
    @Test void revealStaysAvailableUntilAllThreeCardsAreShown() {
        for(int revealed=0;revealed<3;revealed++) {
            assertTrue(has(ZJHControls.Kind.REVEAL,ZJHControls.buttons(revealed,1,1)),
                "已翻 "+revealed+" 张时明牌入口必须还在");
        }
        assertFalse(has(ZJHControls.Kind.REVEAL,ZJHControls.buttons(3,1,1)),"三张全翻开后不再提供明牌");
        assertEquals(5,ZJHControls.buttons(0,1,1).size());
        assertEquals(5,ZJHControls.buttons(2,1,1).size());
        assertEquals(4,ZJHControls.buttons(3,1,1).size());
    }

    @Test void amountsDoubleAfterTheFirstRevealAndRaisesAreAnteMultiples() {
        var dark=ZJHControls.buttons(0,1,1);
        assertEquals(1,dark.get(1).cost());assertEquals(2,dark.get(2).cost());assertEquals(4,dark.get(3).cost());
        assertEquals(ZJHControls.Kind.FOLD,dark.getLast().kind());
        var light=ZJHControls.buttons(1,1,1);
        assertEquals(2,light.get(1).cost());assertEquals(4,light.get(2).cost());assertEquals(8,light.get(3).cost());
        assertEquals(ZJHControls.Kind.FOLD,light.getLast().kind());
        // 1/2/3 张都是同一个明注倍率：翻到第 2、3 张不会再加价
        assertEquals(2,find(ZJHControls.Kind.CALL,ZJHControls.buttons(2,1,1)).cost());
        assertEquals(2,find(ZJHControls.Kind.CALL,ZJHControls.buttons(3,1,1)).cost());
    }

    @Test void unavailableRaisesStayVisibleAndCannotBeSelected() {
        var buttons=ZJHControls.buttons(1,5,20);
        assertTrue(find(ZJHControls.Kind.CALL,buttons).enabled());
        assertFalse(buttons.get(2).enabled());assertFalse(buttons.get(3).enabled());
        assertEquals(40,find(ZJHControls.Kind.CALL,buttons).cost());
    }

    @Test void enteringRevealModeDoesNotPretendCardsHaveBeenSeen() {
        var state=new ZJHControls.Selection();state.enterCards(0);
        assertTrue(state.cards());assertEquals(0,state.mask());
        state.toggle(1,0);assertEquals(2,state.mask());
        state.confirmedReveal();assertFalse(state.cards());assertEquals(0,state.mask());
    }

    @Test void revealedSlotsCannotBeSelectedAndLeavingTurnClearsLocalState() {
        var state=new ZJHControls.Selection();state.enterCards(0);
        state.toggle(0,1);assertEquals(0,state.mask());state.toggle(2,1);assertEquals(4,state.mask());
        state.reset();assertFalse(state.cards());assertEquals(0,state.mask());
    }
}
