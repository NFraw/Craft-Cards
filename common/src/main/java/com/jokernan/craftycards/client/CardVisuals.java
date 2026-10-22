package com.jokernan.craftycards.client;

import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.item.*;

/** Same cached item models for both games; no duplicate card textures. */
public final class CardVisuals {
    private CardVisuals(){}
    public static void draw(GuiGraphics g,ItemStack stack,float x,float y,float scale,float rotation,boolean bottom,float depth) {
        Minecraft mc=Minecraft.getInstance();if(mc.level==null)return;
        var pose=g.pose();pose.pushPose();pose.translate(x,y,depth);
        pose.mulPose(Axis.ZP.rotationDegrees(rotation));pose.translate(0,bottom?-.5F*scale:0,0);
        pose.scale(scale,-scale,1);
        mc.getItemRenderer().renderStatic(stack,ItemDisplayContext.GUI,0xF000F0,OverlayTexture.NO_OVERLAY,
            pose,g.bufferSource(),mc.level,0);pose.popPose();
    }
    public static void card(GuiGraphics g,int id,int x,int y,int width,int depth) {
        draw(g,id<0?CardStacks.covered():CardStacks.face(id),x,y,CardGameHudLayout.cardScale(width),0,false,depth);
    }
}
