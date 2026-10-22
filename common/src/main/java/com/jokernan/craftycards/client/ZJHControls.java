package com.jokernan.craftycards.client;

import java.util.ArrayList;
import java.util.List;

/** Pure UI policy. Entering card selection never changes the authoritative seen/unseen state. */
public final class ZJHControls {
    public enum Kind { REVEAL, CALL, RAISE, FOLD }
    public record Button(Kind kind,String label,long cost,int value,boolean enabled) {}
    private ZJHControls() {}

    /** 按钮在 HUD 选项行上的最终文字：标签 + 费用（费用为 0 时不带后缀）。 */
    public static String label(Button b){return b.label()+(b.cost()>0?" "+b.cost():"");}

    /** First revealed card doubles fees; only the third removes the reveal-entry button. */
    public static List<Button> buttons(int revealedCount,int ante,int base) {
        int shown=Math.max(0,Math.min(3,revealedCount));
        int multiplier=shown>0?2:1;
        List<Button> result=new ArrayList<>();
        if(shown<3)result.add(new Button(Kind.REVEAL,"明牌",0,0,true));
        result.add(new Button(Kind.CALL,"跟注",(long)base*multiplier,base,true));
        result.add(new Button(Kind.RAISE,"加注",(long)ante*2*multiplier,ante*2,ante*2>base));
        result.add(new Button(Kind.RAISE,"加倍",(long)ante*4*multiplier,ante*4,ante*4>base));
        result.add(new Button(Kind.FOLD,"弃牌",0,0,true));
        return List.copyOf(result);
    }
    public static final class Selection {
        private boolean cards;
        private int mask;
        public boolean cards(){return cards;}
        public int mask(){return mask;}
        /** Starts one selection operation at the first unrevealed card. */
        public int enterCards(int revealedMask) {
            reset();int remaining=(~revealedMask)&7;
            if(remaining==0)return -1;
            cards=true;return Integer.numberOfTrailingZeros(remaining);
        }
        public void toggle(int position,int revealedMask) {
            if(cards&&position>=0&&position<3&&(revealedMask&(1<<position))==0)mask^=1<<position;
        }
        public void confirmedReveal(){reset();}
        public void reset(){cards=false;mask=0;}
    }
}
