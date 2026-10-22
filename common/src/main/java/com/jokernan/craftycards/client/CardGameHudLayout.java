package com.jokernan.craftycards.client;

/** Shared, Minecraft-independent HUD contract for every card game. Units are design pixels. */
public final class CardGameHudLayout {
    public static final int DESIGN_HEIGHT=320;
    public static final int ACTION_OFFSET=180;
    public static final int CLOSE_RESERVE=54;
    public static final float MODEL_WIDTH=10.2F;
    public static final int COLOR_TEXT_BACK=0xE0101418;
    public record Viewport(int width,int height,float scale) {}
    private CardGameHudLayout() {}
    public static Viewport viewport(int width,int height) {
        if(width<=0||height<=0)return new Viewport(width,height,1F);
        float scale=height/(float)DESIGN_HEIGHT;
        return new Viewport(Math.round(width/scale),DESIGN_HEIGHT,scale);
    }
    public static float cardScale(int width){return width*16F/MODEL_WIDTH;}
    public static int cardHeight(int width){return Math.round(cardScale(width));}
    public static int closeY(int height){return height-CLOSE_RESERVE;}
    /** The vanilla hotbar stays in GUI units while our canvas scales. Reserve its real height. */
    public static int bottomMargin(int configured,float scale){return Math.max(configured,(int)Math.ceil(26/Math.max(.01F,scale)));}
}
