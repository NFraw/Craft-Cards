package com.jokernan.craftycards.client;

import com.jokernan.craftycards.game.DDZGeom;
import com.jokernan.craftycards.init.InitItems;
import com.jokernan.craftycards.platform.WorldRenderHook;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.item.*;
import net.minecraft.world.phys.*;

/** Public-only world projection: never consumes a hand ID, even if the viewer owns that hand. */
public final class WorldZJH {
    private record Label(Vec3 position,String text,int color) {}
    private static ItemStack empty;
    private static ItemStack chip=ItemStack.EMPTY;
    private static String chipId="";
    private WorldZJH(){}
    public static void render(WorldRenderHook.Context ctx) {
        var s=ZJHClient.snapshot();Minecraft mc=Minecraft.getInstance();
        if(s==null||mc.level==null||mc.player==null||s.closed()||!s.phase().equals("PLAYING"))return;
        var buffer=mc.renderBuffers().bufferSource();Vec3 camera=ctx.camera().getPosition();
        var labels=new java.util.ArrayList<Label>();
        float partial=ctx.partialTick().getGameTimeDeltaPartialTick(true);
        if(empty==null)empty=new ItemStack(InitItems.CARD_EMPTY.get());
        for(int i=0;i<s.players().size();i++) {
            var row=s.players().getCompound(i);var holder=mc.level.getPlayerByUUID(row.getUUID("uuid"));
            if(holder==null||(holder==mc.player&&mc.options.getCameraType().isFirstPerson()))continue;
            if(holder.distanceToSqr(camera)>RenderConfig.handMaxRenderDist*RenderConfig.handMaxRenderDist
                ||!ctx.frustum().isVisible(holder.getBoundingBox().inflate(3)))continue;
            Vec3 at=holder.getPosition(partial);
            float yaw=holder.getViewYRot(partial);
            double fx=DDZGeom.forwardX(yaw),fz=DDZGeom.forwardZ(yaw);
            Vec3 center=at.add(fx*RenderConfig.handForwardDist,RenderConfig.handHeight,fz*RenderConfig.handForwardDist);
            int light=LevelRenderer.getLightColor(mc.level,holder.blockPosition());
            var metadata=ZJHWorldLayout.labels(row.getBoolean("active"),row.getLong("paid"),row.getString("recent"),
                i==s.data().getInt("dealer"),i==s.data().getInt("current"));
            if(row.getBoolean("active")) {
                var slots=ZJHWorldLayout.cards(RenderConfig.handCardScale,RenderConfig.handCardGap,RenderConfig.handStackStep,RenderConfig.handFanAngle);
                for(int c=0;c<3;c++) {
                    PoseStack pose=new PoseStack();
                    pose.translate(center.x-camera.x,center.y-camera.y,center.z-camera.z);
                    pose.mulPose(Axis.YP.rotationDegrees(DDZGeom.facingYaw(-fx,-fz)));
                    pose.translate(slots[c].x(),0,slots[c].z());
                    pose.mulPose(Axis.YP.rotationDegrees(slots[c].angle()));
                    pose.mulPose(Axis.XP.rotationDegrees(90));
                    pose.scale(RenderConfig.handCardScale,RenderConfig.handCardScale,RenderConfig.handCardScale);
                    mc.getItemRenderer().renderStatic(c<row.getInt("revealed")?empty:CardStacks.covered(),
                        ItemDisplayContext.NONE,light,OverlayTexture.NO_OVERLAY,pose,buffer,mc.level,0);
                }
                if(!metadata.turn().isEmpty())labels.add(new Label(center.add(0,RenderConfig.handCardScale*.5+.2,0),"[ 行动中 ]",0xFFFFD700));
            } else labels.add(new Label(center,metadata.center(),0xFFFF6666));
            // Billboard columns are placed to the observer's left/right, outside the card silhouette.
            double screenRightX=DDZGeom.forwardZ(ctx.camera().getYRot());
            double screenRightZ=-DDZGeom.forwardX(ctx.camera().getYRot());
            labels.add(new Label(center.add(-screenRightX*1.15,.12,-screenRightZ*1.15),metadata.left(),0xFFFFFFFF));
            labels.add(new Label(center.add(screenRightX*1.2,.12,screenRightZ*1.2),mc.font.plainSubstrByWidth(metadata.right(),72),0xFFFFFFFF));
            if(!metadata.dealer().isEmpty())labels.add(new Label(at.add(0,holder.getBbHeight()+.9,0),"[ 庄家 ]",0xFFFFD700));
        }
        if(s.data().getBoolean("physical")) {
            String id=s.data().getCompound("config").getString("chip");
            if(!id.equals(chipId)) {
                chipId=id;var resource=net.minecraft.resources.ResourceLocation.tryParse(id);
                chip=resource==null?ItemStack.EMPTY:new ItemStack(net.minecraft.core.registries.BuiltInRegistries.ITEM.get(resource));
            }
            Vec3 pos=Vec3.atCenterOf(s.table().pos()).add(0,.9,0);
            if(!chip.isEmpty()&&camera.distanceToSqr(pos)<32*32&&ctx.frustum().isVisible(new AABB(pos,pos).inflate(.5))) {
                PoseStack pose=new PoseStack();pose.translate(pos.x-camera.x,pos.y-camera.y,pos.z-camera.z);
                pose.mulPose(Axis.YP.rotationDegrees((ctx.renderTick()+partial)*2));pose.scale(.4F,.4F,.4F);
                mc.getItemRenderer().renderStatic(chip,ItemDisplayContext.GROUND,LevelRenderer.getLightColor(mc.level,s.table().pos().above()),
                    OverlayTexture.NO_OVERLAY,pose,buffer,mc.level,0);
            }
        }
        // Finish item geometry before text. Text has its own depth-biased pipeline and opaque glyph colors.
        buffer.endBatch();
        for(Label label:labels)text(ctx,label.position(),label.text(),label.color());
        buffer.endBatch();
    }
    private static void text(WorldRenderHook.Context ctx,Vec3 location,String label,int color) {
        Minecraft mc=Minecraft.getInstance();Vec3 camera=ctx.camera().getPosition();
        PoseStack pose=new PoseStack();pose.translate(location.x-camera.x,location.y-camera.y,location.z-camera.z);
        pose.mulPose(ctx.camera().rotation());pose.scale(-.024F,-.024F,.024F);
        mc.font.drawInBatch(label,-mc.font.width(label)/2F,0,color,true,pose.last().pose(),
            mc.renderBuffers().bufferSource(),Font.DisplayMode.POLYGON_OFFSET,0xAA080808,0xF000F0);
    }
}
