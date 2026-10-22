package com.jokernan.craftycards.client;

import com.jokernan.craftycards.game.DDZGeom;

/** World projection geometry and public labels, independent of Minecraft. */
public final class ZJHWorldLayout {
    public record Slot(float x,float z,float angle) {}
    public record Labels(String center,String left,String right,String dealer,String turn) {}
    private ZJHWorldLayout() {}
    public static Slot[] cards(float scale,float gap,float requestedStep,float fan) {
        // card_base is 0.5/16 thick. Also allow for the edge rotation of adjacent fanned cards.
        float thickness=scale/32f;
        float edgeAllowance=scale*9f/32f*(float)Math.abs(Math.sin(Math.toRadians(fan)));
        float step=Math.max(requestedStep,thickness+edgeAllowance+.004f);
        Slot[] slots=new Slot[3];float[] depths=new float[3];
        for(int i=1;i<3;i++)depths[i]=depths[i-1]+DDZGeom.fanDepthAdvance(gap,step,(i-2)*fan);
        for(int i=0;i<3;i++)slots[i]=new Slot((i-1)*gap,depths[i]-depths[1],(i-1)*fan);
        return slots;
    }
    public static Labels labels(boolean active,long paid,String recent,boolean dealer,boolean current) {
        return new Labels(active?"":"出局","累计投入 "+paid,recent,dealer?"庄":"",active&&current?"行动中":"");
    }
}
