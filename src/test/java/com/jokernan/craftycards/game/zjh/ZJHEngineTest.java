package com.jokernan.craftycards.game.zjh;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class ZJHEngineTest {
    @Test void everyLiveSeatActsOncePerRotationAcrossNewDealsAndDealerChanges() {
        for(int n=2;n<=6;n++)for(int dealer=0;dealer<n;dealer++)for(int hand=0;hand<10;hand++) {
            var g=ZJHEngine.deal(n,1,1000,dealer,new Random(hand));
            for(int round=1;round<=5;round++) {
                Set<Integer> acted=new HashSet<>();
                for(int step=0;step<n;step++) {
                    int seat=g.current();
                    assertEquals((dealer+1+step)%n,seat);
                    assertTrue(g.active(seat));assertTrue(acted.add(seat),"同一轮不能重复行动而漏掉别人");
                    assertEquals(round,g.round());g.call(seat);
                }
                assertEquals(n,acted.size());
            }
        }
    }
    static int c(int rank, int suit) { return (rank - 1) * 4 + suit; }
    static int[] h(int a, int b, int c) { return new int[]{a,b,c}; }
    @Test void specialIsLowestExceptAgainstTripsAndWinsMultiwayWithTrips() {
        int[] special = h(c(2,0),c(3,1),c(5,2));
        int[] trips = h(c(1,0),c(1,1),c(1,2));
        int[] straightFlush = h(c(7,3),c(8,3),c(9,3));
        assertTrue(ZJHHandEvaluator.compare(special,trips)>0);
        assertTrue(ZJHHandEvaluator.compare(trips,straightFlush)>0);
        assertTrue(ZJHHandEvaluator.compare(straightFlush,special)>0);
        assertEquals(List.of(0), ZJHHandEvaluator.winners(List.of(special,trips,straightFlush)));
        assertEquals(List.of(1), ZJHHandEvaluator.winners(List.of(special,straightFlush)));
    }
    @Test void aceLowStraightIsSecondAndPairComparesPairFirst() {
        assertTrue(ZJHHandEvaluator.compare(h(c(1,0),c(2,1),c(3,2)),h(c(11,1),c(12,2),c(13,3)))>0);
        assertTrue(ZJHHandEvaluator.compare(h(c(12,1),c(13,2),c(1,3)),h(c(1,0),c(2,1),c(3,2)))>0);
        assertTrue(ZJHHandEvaluator.compare(h(c(4,0),c(4,1),c(2,0)),h(c(3,0),c(3,1),c(1,0)))>0);
    }
    @Test void everyThreeCardHandClassifiesAndPermutationIsIrrelevant() {
        int count=0;
        for(int a=0;a<52;a++) for(int b=a+1;b<52;b++) for(int c=b+1;c<52;c++) {
            var value=ZJHHandEvaluator.evaluate(h(a,b,c));
            assertEquals(value,ZJHHandEvaluator.evaluate(h(c,a,b)));
            count++;
        }
        assertEquals(22100,count);
        assertThrows(IllegalArgumentException.class,()->ZJHHandEvaluator.evaluate(h(0,0,2)));
    }
    static ZJHEngine game(int cap) {
        return new ZJHEngine(new int[][]{h(0,5,14),h(12,17,22),h(24,29,34)},1,cap,2);
    }
    @Test void revealIsTurnBoundMonotonicAndDoesNotAdvanceTurn() {
        var g=game(100);
        assertThrows(IllegalArgumentException.class,()->g.reveal(1,1));
        assertTrue(g.reveal(0,1));
        assertEquals(0,g.current()); assertEquals(1,g.round());
        assertEquals(2,g.callCost(0)); assertEquals(1,g.revealedCount(0));
        assertFalse(g.reveal(0,1));
        g.reveal(0,6); assertEquals(3,g.revealedCount(0));
        assertEquals(2,g.callCost(0)); assertEquals(3,g.pot());
    }
    @Test void compareUnlocksAfterTwoWholeRoundsAndTargetPaysNothing() {
        var g=game(100);
        assertThrows(IllegalArgumentException.class,()->g.compare(0,1));
        for(int i=0;i<6;i++) g.call(g.current());
        assertEquals(3,g.round());
        long targetPaid=g.contribution(1), total=g.pot();
        g.compare(0,1);
        assertEquals(targetPaid,g.contribution(1)); assertEquals(total+2,g.pot());
        assertFalse(g.active(0)); assertTrue(g.active(1)); assertEquals(1,g.current());
    }
    @Test void identicalHandStrengthLosesForInitiator() {
        var g=new ZJHEngine(new int[][]{h(0,4,12),h(1,5,13)},1,100,1);
        for(int i=0;i<4;i++)g.call(g.current());
        g.compare(0,1); assertTrue(g.settled()); assertEquals(List.of(1),g.winners());
    }
    @Test void capAndFoldAlwaysConserveLedger() {
        var g=game(3); g.call(0);g.call(1);g.call(2);g.call(0);
        assertTrue(g.settled());
        long total=0;for(int i=0;i<3;i++)total+=g.payout(i);
        assertEquals(g.pot(),total);
        var f=game(100);f.fold(1);f.fold(0);
        assertEquals(List.of(2),f.winners());assertEquals(3,f.payout(2));
    }
    @Test void oversizedFeeSettlesWithoutPartialDebit() {
        var g=game(3);g.raise(0,2);
        assertTrue(g.settled()); assertEquals(3,g.contribution(0));
        var f=game(3);f.reveal(0,1);f.raise(0,4);
        assertTrue(f.settled());assertEquals(1,f.contribution(0));assertEquals(3,f.pot());
    }
    @Test void randomGamesTerminateWithUniqueCardsAndConservedPot() {
        var random=new Random(42);
        for(int n=2;n<=6;n++)for(int run=0;run<50;run++) {
            var g=ZJHEngine.deal(n,1,40,run%n,random);
            int actions=0;
            while(!g.settled() && actions++<500) {
                int p=g.current();
                if(random.nextBoolean())g.reveal(p,1<<random.nextInt(3));
                if(random.nextInt(8)==0)g.fold(p);else g.call(p);
            }
            assertTrue(g.settled());
            long sum=0;for(int p=0;p<n;p++)sum+=g.payout(p);
            assertEquals(g.pot(),sum);
        }
    }
}
