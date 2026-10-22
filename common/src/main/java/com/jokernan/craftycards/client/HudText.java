package com.jokernan.craftycards.client;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

/** Small text backplates keep world name tags and terrain from interfering with the HUD. */
final class HudText {
    private HudText() {}
    static void back(GuiGraphics g,Font font,int center,int y,int width) {
        g.fill(center-width/2-3,y-2,center+(width+1)/2+3,y+font.lineHeight+2,CardGameHudLayout.COLOR_TEXT_BACK);
    }
    static void centered(GuiGraphics g,Font font,String text,int center,int y,int color) {
        if(text.isEmpty())return;
        back(g,font,center,y,font.width(text));
        g.drawCenteredString(font,text,center,y,color);
    }
}
