package com.jokernan.craftycards.smoke;

import com.jokernan.craftycards.client.ZJHClient;
import com.jokernan.craftycards.network.payload.TableKey;
import net.minecraft.client.*;
import net.minecraft.client.gui.screens.*;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import java.nio.file.*;
import java.util.*;

/** Real render/network test through the production input handlers; screenshots are not sketches. */
public final class ZjhPocClient {
    private final TableKey key=new TableKey(Level.OVERWORLD,PocServer.TABLE);
    private final Set<String> shots=new HashSet<>();
    private int ticks,worldAt,after,step,reveals,settledAt,scaleStep,configStep,tableShotStep;
    private boolean connected,joined;
    public void initialize(java.util.function.Consumer<java.util.function.Consumer<Minecraft>> register) {
        register.accept(mc->{try{tick(mc);}catch(Throwable e){e.printStackTrace();result("FAIL: "+e);mc.stop();}});
    }
    private void tick(Minecraft mc) {
        ticks++;
        if(ticks>12000)throw new AssertionError("ZJH client timeout step="+step);
        if(!connected) {
            if(mc.screen==null||mc.getOverlay()!=null||ticks<60)return;
            mc.options.pauseOnLostFocus=false;mc.options.guiScale().set(2);mc.options.renderDistance().set(3);
            mc.options.framerateLimit().set(30);mc.options.hideGui=false;
            ConnectScreen.startConnecting(new TitleScreen(),mc,ServerAddress.parseString("127.0.0.1:25576"),
                new ServerData("ZJH local test","127.0.0.1:25576",ServerData.Type.OTHER),false,null);
            connected=true;return;
        }
        if(mc.player==null||mc.level==null||mc.gameMode==null)return;
        mc.getToasts().clear();
        if(worldAt==0)worldAt=ticks;
        int n=Integer.parseInt(mc.getUser().getName().substring(5));
        Vec3 d=Vec3.atCenterOf(PocServer.TABLE).subtract(mc.player.getEyePosition());
        mc.player.setYRot((float)Math.toDegrees(Math.atan2(-d.x,d.z)));
        mc.player.setXRot((float)-Math.toDegrees(Math.atan2(d.y,Math.sqrt(d.x*d.x+d.z*d.z))));
        if(ticks<after||ticks-worldAt<40*n)return;
        if(n==1&&configStep<3) {
            if(configStep==0){ZJHClient.tableClicked(key,true);configStep=1;after=ticks+15;return;}
            if(configStep==1) {
                check(mc.screen instanceof com.jokernan.craftycards.client.ZJHConfigScreen,"server opened ZJH configuration");
                shot(mc,"zjh-config.png");ZJHClient.saveConfig(ZJHClient.snapshot().data().getCompound("config"));
                configStep=2;after=ticks+15;return;
            }
            check(!(mc.screen instanceof com.jokernan.craftycards.client.ZJHConfigScreen),"saving releases config lock and closes screen");
            configStep=3;
        }
        mc.setScreen(null);
        if(n==1&&tableShotStep<2) {
            if(tableShotStep==0){mc.player.setPos(1.5,67,4.5);tableShotStep=1;after=ticks+15;}
            Vec3 look=new Vec3(1.5,65.5,.5).subtract(mc.player.getEyePosition());
            mc.player.setYRot((float)Math.toDegrees(Math.atan2(-look.x,look.z)));
            mc.player.setXRot((float)-Math.toDegrees(Math.atan2(look.y,Math.sqrt(look.x*look.x+look.z*look.z))));
            if(ticks<after)return;
            shot(mc,"table-game-colors.png");mc.player.setPos(.5,65,3);tableShotStep=2;after=ticks+15;return;
        }
        if(!joined){ZJHClient.tableClicked(key,false);joined=true;after=ticks+15;return;}
        var p=ZJHClient.snapshot();
        if(p==null) {
            if(settledAt>0){shot(mc,"zjh-dismissed.png");result("PASS: ZJH join, reveal 1/2/3, call, target compare, private snapshots, full result, right-click dismissal");mc.stop();}
            return;
        }
        if(p.phase().equals("WAITING")){shot(mc,"zjh-waiting.png");return;}
        if(p.phase().equals("SETTLED")) {
            for(int i=0;i<p.players().size();i++)for(int c:p.players().getCompound(i).getIntArray("hand"))check(c>=0,"settlement reveals every hand");
            if(settledAt==0){settledAt=ticks;after=ticks+15;return;}
            if(!shots.contains("zjh-settled.png")){shot(mc,"zjh-settled.png");after=ticks+20;return;}
            ZJHClient.mouse(1,1);check(ZJHClient.snapshot()==null,"right click dismisses result");after=ticks+8;return;
        }
        // The last joiner never observes WAITING; capture that state only on clients that actually saw it.
        for(int i=0;i<p.players().size();i++)if(i!=p.me())for(int c:p.players().getCompound(i).getIntArray("hand"))check(c==-1,"opponent card leaked");
        shot(mc,"zjh-dark.png");
        if(!ZJHClient.turn()||ZJHClient.busy())return;
        if(reveals<3) {
            if(step==0){ZJHClient.tableClicked(key,false);step=1;after=ticks+8;return;}
            if(step==1){shot(mc,"zjh-selecting.png");ZJHClient.mouse(0,1);step=2;after=ticks+5;return;}
            if(step==2){ZJHClient.tableClicked(key,false);step=3;after=ticks+8;return;}
            if(step==3){reveals=p.players().getCompound(p.me()).getInt("revealed");shot(mc,"zjh-revealed-"+reveals+".png");step=0;after=ticks+8;return;}
        }
        if(scaleStep<4) {
            if(scaleStep==0) {
                org.lwjgl.glfw.GLFW.glfwSetWindowSize(mc.getWindow().getWindow(),1280,720);
                mc.options.guiScale().set(4);mc.resizeDisplay();scaleStep=1;after=ticks+20;return;
            }
            if(scaleStep==1) {
                shot(mc,"zjh-small.png");
                org.lwjgl.glfw.GLFW.glfwSetWindowSize(mc.getWindow().getWindow(),960,640);
                mc.options.guiScale().set(2);mc.resizeDisplay();scaleStep=2;after=ticks+15;return;
            }
            if(scaleStep==2&&n==1) {
                mc.options.guiScale().set(0);mc.getWindow().toggleFullScreen();mc.resizeDisplay();
                scaleStep=3;after=ticks+25;return;
            }
            if(scaleStep==3) {
                shot(mc,"zjh-fullscreen.png");mc.getWindow().toggleFullScreen();
                mc.options.guiScale().set(2);mc.resizeDisplay();scaleStep=4;after=ticks+15;return;
            }
            scaleStep=4;
        }
        if(p.data().getInt("round")<3) {
            ZJHClient.tableClicked(key,false);after=ticks+12;return; // Fully revealed: focus 0 = call.
        }
        if(step==0) {
            for(int i=0;i<4;i++)ZJHClient.scroll(-1); // call/raise/double/fold/compare
            ZJHClient.tableClicked(key,false);step=4;after=ticks+12;return;
        }
        if(step==4) {
            shot(mc,"zjh-target.png");ZJHClient.tableClicked(key,false);step=0;after=ticks+15;
        }
    }
    private void shot(Minecraft mc,String name){if(shots.add(name)){Screenshot.grab(mc.gameDirectory,name,mc.getMainRenderTarget(),message->{});CaptureMetadata.write(mc,name);}}
    private static void check(boolean ok,String detail){if(!ok)throw new AssertionError(detail);}
    private static void result(String text){try{Files.writeString(Path.of("poc-result.txt"),text);}catch(Exception e){throw new RuntimeException(e);}}
}
