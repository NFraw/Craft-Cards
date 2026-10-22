package com.jokernan.craftycards.client;

import com.jokernan.craftycards.game.zjh.ZJHHandEvaluator;
import com.jokernan.craftycards.network.payload.*;
import com.jokernan.craftycards.platform.Network;
import net.minecraft.client.*;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.BlockHitResult;
import java.util.*;
import static com.jokernan.craftycards.network.payload.ZJHActionPayload.Action;

/**
 * 炸金花 HUD。视觉与斗地主共用同一套语言：
 * 标题白色居中、阶段行灰、可操作提示绿、选项是<b>纯文字 + 金色焦点</b>（不再是灰框按钮），
 * 结算用整屏半透明遮罩 + 礼花（不再是深色内嵌面板）。
 *
 * <p>加入即就绪，满配置人数自动开局。结算自动退局，HUD 只保留结果，右键任意位置关闭。</p>
 */
public final class ZJHClient {
    /** 结算面板上一项可选操作。{@code target>=0} 表示"以该座位为比牌目标"。 */
    private record Choice(String label,Action action,int value,int target,boolean enabled) {}
    private static ZJHStatePayload snapshot;
    private static TableKey requested;
    private static final ZJHControls.Selection selection=new ZJHControls.Selection();
    private static int actionFocus,cardFocus,targetFocus;
    private static boolean choosingTarget;
    private static UUID dismissedSession;
    private static long dismissedHand=-1;
    private static long sequence=System.nanoTime(),receivedAt,sentAt;
    private static ZJHActionPayload pending;
    private static String message="";
    private static long messageUntil;
    /** 「离开牌局」发出后最多等这么久；超时本地收尾，绝不让结算画面永久占屏。 */
    private static final long LEAVE_TIMEOUT_MS=3000;
    /** 任何动作超过这么久没回执就放掉 pending：否则 busy() 会一直为真，右键全无反应。 */
    private static final long PENDING_TIMEOUT_MS=5000;
    /** 刚离开牌局后的这段时间内忽略 JOIN，避免同一次点击"退出又加入"来回弹。 */
    private static final long LEAVE_JOIN_GUARD_MS=800;
    private static long leftAt;
    private ZJHClient() {}
    public static ZJHStatePayload snapshot(){return snapshot;}
    public static boolean busy(){return pending!=null;}
    public static void accept(ZJHStatePayload p) {
        if(requested!=null&&!requested.equals(p.table())&&(snapshot==null||!snapshot.table().equals(p.table())))return;
        if(snapshot!=null&&snapshot.session().equals(p.session())&&p.version()<snapshot.version())return;
        sequence=Math.max(sequence,p.acknowledgement());
        if(p.closed()) {
            if(pending!=null&&pending.table().equals(p.table()))pending=null;
            if(snapshot==null||snapshot.table().equals(p.table()))reset();else requested=snapshot.table();
            if(!p.data().getString("feedback").isEmpty()&&Minecraft.getInstance().player!=null)
                Minecraft.getInstance().player.displayClientMessage(Component.literal(p.data().getString("feedback")),false);
            return;
        }
        boolean confirmedReveal=pending!=null&&pending.action()==Action.REVEAL&&p.acknowledgement()>=pending.sequence();
        if(pending!=null&&p.acknowledgement()>=pending.sequence())pending=null;
        if(p.session().equals(dismissedSession)&&p.data().getLong("handNumber")==dismissedHand)return;
        var old=snapshot;
        boolean newHand=old==null||!old.session().equals(p.session())
            ||old.data().getLong("handNumber")!=p.data().getLong("handNumber")||!old.phase().equals(p.phase());
        if(newHand||old.data().getInt("current")!=p.data().getInt("current"))resetFocus();
        snapshot=p;requested=p.table();receivedAt=System.currentTimeMillis();
        if(p.phase().equals("SETTLED"))pending=null;
        if(confirmedReveal){selection.confirmedReveal();actionFocus=0;}
        if(!turn())selection.reset();
        if(!p.data().getString("feedback").isEmpty()){message=p.data().getString("feedback");messageUntil=receivedAt+5000;}
        ClientDDZData.getInstance().reset();
        if(p.me()>=0&&(old==null||newHand)) {
            if(p.phase().equals("PLAYING"))ClientSounds.deal();
            if(p.phase().equals("SETTLED"))ClientSounds.settlement(me().getBoolean("winner"));
        }
        if(p.data().getBoolean("configOpen")&&!(Minecraft.getInstance().screen instanceof ZJHConfigScreen))
            Minecraft.getInstance().setScreen(new ZJHConfigScreen());
        // Settlement has a single leave action; joining the next hand is a separate decision.
        actionFocus=p.phase().equals("SETTLED")?Math.max(0,choices().size()-1):Math.floorMod(actionFocus,Math.max(1,choices().size()));
    }
    private static void resetFocus(){selection.reset();actionFocus=0;cardFocus=0;targetFocus=0;choosingTarget=false;}
    public static void reset(){snapshot=null;requested=null;pending=null;resetFocus();}
    public static void tick() {
        Minecraft mc=Minecraft.getInstance();if(mc.level==null||mc.player==null){reset();return;}
        if(snapshot!=null&&!snapshot.table().dimension().equals(mc.level.dimension())){reset();return;}
        if(pending==null)return;
        long waited=System.currentTimeMillis()-sentAt;
        // 退出是终态：重发两次仍无回执就本地收尾。服务端迟早会按掉线/空闲清位，
        // 但玩家不该为此被钉在结算遮罩上——这正是"退不出来"的直接来源。
        if(pending.action()==Action.LEAVE) {
            if(waited>LEAVE_TIMEOUT_MS){pending=null;reset();return;}
        } else if(waited>PENDING_TIMEOUT_MS) {
            // 服务端没回执（掉线/被拒但没收到响应）时也要放掉，否则 busy() 永久为真，
            // 玩家会以为"右键没反应"，只能反复点。
            pending=null;message="操作超时，请重试";messageUntil=System.currentTimeMillis()+3000;return;
        }
        if(waited>2000){sentAt=System.currentTimeMillis();Network.sendZjh(pending);}
    }
    private static CompoundTag me(){return snapshot.players().getCompound(snapshot.me());}
    private static int revealedMask() {
        int mask=0;int[] hand=me().getIntArray("hand");
        for(int i=0;i<hand.length;i++)if(hand[i]>=0)mask|=1<<i;return mask;
    }
    public static boolean turn() {
        return snapshot!=null&&snapshot.phase().equals("PLAYING")&&snapshot.me()>=0&&snapshot.me()<snapshot.players().size()
            &&snapshot.me()==snapshot.data().getInt("current")&&me().getBoolean("present")&&me().getBoolean("active");
    }
    private static int ante(){return snapshot.data().getCompound("config").getInt("ante");}
    private static int base(){return snapshot.data().getInt("base");}
    private static boolean compareAvailable(){return turn()&&snapshot.data().getInt("round")>=3;}
    private static long compareCost(){return (long)base()*(me().getInt("revealed")>0?4:2);}
    private static List<Integer> targets() {
        var result=new ArrayList<Integer>();if(snapshot!=null)for(int i=0;i<snapshot.players().size();i++)
            if(i!=snapshot.me()&&snapshot.players().getCompound(i).getBoolean("active"))result.add(i);
        return result;
    }

    /**
     * 当前阶段可选的行动，顺序即滚轮顺序、也即绘制顺序。
     * 绘制与点击都读这一份，避免"画的是一个、点的是另一个"。
     */
    private static List<Choice> choices() {
        if(snapshot==null)return List.of();
        String phase=snapshot.phase();
        if(phase.equals("SETTLED")||snapshot.me()<0)return List.of();
        if(phase.equals("WAITING")) {
            return List.of(new Choice("离开牌局",Action.LEAVE,0,-1,true));
        }
        if(choosingTarget) {
            var result=new ArrayList<Choice>();
            for(int seat:targets())result.add(new Choice(snapshot.players().getCompound(seat).getString("name"),Action.COMPARE,0,seat,true));
            return result;
        }
        var result=new ArrayList<Choice>();
        for(var b:ZJHControls.buttons(me().getInt("revealed"),ante(),base())) {
            var action=switch(b.kind()) {
                case REVEAL -> Action.REVEAL;
                case CALL -> Action.CALL;
                case RAISE -> Action.RAISE;
                case FOLD -> Action.FOLD;
            };
            result.add(new Choice(ZJHControls.label(b),action,b.value(),-1,b.enabled()));
        }
        if(compareAvailable())result.add(new Choice("比牌 "+compareCost(),Action.COMPARE,0,-2,true));
        return result;
    }
    private static void activate(int index) {
        if(snapshot==null||(snapshot.phase().equals("PLAYING")&&!turn()))return;
        var list=choices();if(index<0||index>=list.size())return;
        var choice=list.get(index);if(!choice.enabled()||busy())return;
        switch(choice.action()) {
            case REVEAL -> {cardFocus=selection.enterCards(revealedMask());choosingTarget=false;}
            case COMPARE -> {
                if(choice.target()==-2){choosingTarget=true;actionFocus=0;}
                else send(Action.COMPARE,0,choice.target());
            }
            case LEAVE -> {send(Action.LEAVE,0,-1);leaveNow();}
            default -> send(choice.action(),choice.value(),-1);
        }
    }
    public static void tableClicked(TableKey key,boolean shift) {
        if(snapshot!=null&&snapshot.phase().equals("SETTLED")){dismissResult();return;}
        // 刚点了「离开牌局」：同一次点击还会落到方块上，别再顺手把人加回去
        if(snapshot==null&&!shift&&System.currentTimeMillis()-leftAt<LEAVE_JOIN_GUARD_MS)return;
        requested=key;dismissedSession=null;
        if(snapshot==null||!snapshot.table().equals(key)){sendAt(key,shift?Action.CONFIG:Action.JOIN,0,-1,new CompoundTag());return;}
        if(shift){send(snapshot.me()>=0?Action.LEAVE:Action.CONFIG,0,-1);return;}
        if(snapshot.me()<0){send(Action.JOIN,0,-1);return;}
        if(busy())return;
        if(choosingTarget) {var list=choices();if(!list.isEmpty())activate(Math.floorMod(actionFocus,list.size()));return;}
        if(selection.cards()) {if(selection.mask()!=0)send(Action.REVEAL,selection.mask(),-1);return;}
        activate(actionFocus);
    }
    public static void send(Action a,int value,int target){if(snapshot!=null)sendAt(snapshot.table(),a,value,target,new CompoundTag());}
    public static void saveConfig(CompoundTag t){if(snapshot!=null)sendAt(snapshot.table(),Action.SAVE,0,-1,t);}
    private static void sendAt(TableKey key,Action action,int value,int target,CompoundTag config) {
        if(busy())return;
        var same=snapshot!=null&&snapshot.table().equals(key)?snapshot:null;
        pending=new ZJHActionPayload(key,same==null?ZJHActionPayload.NONE:same.session(),same==null?-1:same.version(),++sequence,action,value,target,config);
        sentAt=System.currentTimeMillis();Network.sendZjh(pending);
    }
    /** Clear the local UI. In-game leave requests are sent by the caller; completed hands are already released. */
    private static void leaveNow() {
        if(snapshot==null)return;
        dismissedSession=snapshot.session();dismissedHand=snapshot.data().getLong("handNumber");
        leftAt=System.currentTimeMillis();
        snapshot=null;resetFocus();
    }
    /** The server has already settled and released the seat; this only dismisses a local result. */
    private static void dismissResult() {
        if(snapshot==null)return;
        pending=null;leaveNow();
    }
    public static boolean scroll(double delta) {
        Minecraft mc=Minecraft.getInstance();if(snapshot==null||mc.screen!=null)return false;
        int direction=delta>0?-1:1;
        if(snapshot.phase().equals("SETTLED"))return true;
        if(snapshot.me()<0)return false;
        if(selection.cards())cardFocus=Math.floorMod(cardFocus+direction,3);
        else if(choosingTarget)actionFocus=Math.floorMod(actionFocus+direction,Math.max(1,choices().size()));
        else {
            var list=choices();int count=Math.max(1,list.size());
            for(int tries=0;tries<count;tries++) {
                actionFocus=Math.floorMod(actionFocus+direction,count);
                if(actionFocus>=list.size()||list.get(actionFocus).enabled())break;
            }
        }return true;
    }
    public static boolean mouse(int button,int action) {
        Minecraft mc=Minecraft.getInstance();if(snapshot==null||mc.screen!=null)return false;
        if(snapshot.phase().equals("SETTLED")) {
            if(button==1&&action==1)dismissResult();
            return button==0||button==1;
        }
        if(button==0&&snapshot.me()>=0) {
            if(action==1&&!busy()&&turn()) {
                if(selection.cards())selection.toggle(cardFocus,revealedMask());
            }return true;
        }
        if(button==1&&action==1&&(selection.cards()||choosingTarget)) {
            if(!(mc.hitResult instanceof BlockHitResult hit)||!hit.getBlockPos().equals(snapshot.table().pos())) {
                selection.reset();choosingTarget=false;actionFocus=0;return true;
            }
        }return false;
    }

    // ---------------------------------------------------------------- 绘制

    public static void render(GuiGraphics g,DeltaTracker delta) {
        Minecraft mc=Minecraft.getInstance();if(snapshot==null||mc.player==null||mc.level==null||mc.screen!=null||mc.options.hideGui)return;
        var viewport=CardGameHudLayout.viewport(mc.getWindow().getGuiScaledWidth(),mc.getWindow().getGuiScaledHeight());
        g.pose().pushPose();g.pose().scale(viewport.scale(),viewport.scale(),1);
        try {renderAtViewport(g,viewport.width(),viewport.height());} finally {g.pose().popPose();}
    }
    private static void renderAtViewport(GuiGraphics g,int w,int h) {
        Minecraft mc=Minecraft.getInstance();
        // 底边距跟着斗地主的配置走（/craftycards reload 改了 hudBottomMargin，两个游戏一起变）
        ZJHLayout.setBottomMargin(CardGameHudLayout.bottomMargin(RenderConfig.hudBottomMargin,
            CardGameHudLayout.viewport(mc.getWindow().getGuiScaledWidth(),mc.getWindow().getGuiScaledHeight()).scale()));
        var t=snapshot.data();
        if(snapshot.phase().equals("SETTLED")){result(g,w,h);return;}

        // 标题与斗地主同款：白色、居中、第 4 像素行
        centered(g,"炸金花",w/2,4,0xFFFFFFFF);

        // 第二行：阶段 | 行动者 | 倒计时（倒计时单独上色，越紧越红）
        String phase;
        if(snapshot.phase().equals("WAITING"))phase="加入即就绪 · 人数足够自动开局";
        else {
            int current=t.getInt("current");
            String name=snapshot.players().getCompound(current).getString("name");
            phase="第 "+t.getInt("round")+" 轮 | "+(snapshot.me()<0?"只读观战"
                :!me().getBoolean("active")?"你已出局："+me().getString("recent")
                :turn()?"轮到你行动":"等待 "+name+" 行动");
        }
        int ticks=t.getInt("remaining");
        if(ticks>=0)ticks=(int)Math.max(0,ticks-(System.currentTimeMillis()-receivedAt)/50);
        String countdown=TurnCountdown.label(ticks);
        if(countdown.isEmpty())centered(g,phase,w/2,16,0xFFAAAAAA);
        else {
            var font=mc.font;
            int textX=(w-font.width(phase)-font.width(countdown))/2;
            HudText.back(g,font,w/2,16,font.width(phase)+font.width(countdown));
            g.drawString(font,phase,textX,16,0xFFAAAAAA);
            g.drawString(font,countdown,textX+font.width(phase),16,TurnCountdown.color(ticks));
        }

        // 第三行：公共局势（等待阶段换成"人数 + 筹码"，与斗地主的等待提示同一套颜色）
        if(snapshot.phase().equals("WAITING")) {
            int joined=0;for(Tag e:snapshot.players())if(((CompoundTag)e).getBoolean("present"))joined++;
            int minimum=t.getCompound("config").getInt("minimum");
            String reason=t.getString("waitingReason");
            centered(g,reason.isEmpty()?"等待玩家加入 ("+joined+"/"+minimum+")":reason,w/2,30,0xFFFFAA00);
            String chip=t.getBoolean("physical")?"筹码: "+chipName()+"（开局暂存预算 "+t.getCompound("config").getInt("cap")+"）"
                :"本桌只记虚拟分数";
            centered(g,chip,w/2,46,0xFF55FF55);
        } else {
            centered(g,"锅底 "+t.getLong("pot")+" | 暗注 "+t.getInt("base")+" | 上限 "+t.getCompound("config").getInt("cap")
                +(t.getBoolean("physical")?" | "+chipName():""),w/2,30,0xFFAAAAAA);
        }

        if(snapshot.me()>=0&&snapshot.phase().equals("PLAYING"))drawHand(g,w,h);
        drawActions(g,w,h);
        // 反馈行紧贴操作块上方（与斗地主同一位置关系），不再画在屏幕顶部
        if(System.currentTimeMillis()<messageUntil)centered(g,message,w/2,ZJHLayout.messageY(w,h),HudOptionBar.COLOR_MESSAGE);
    }

    private static void drawHand(GuiGraphics g,int w,int h) {
        var me=me();var layout=ZJHLayout.hand(w,h);int[] hand=me.getIntArray("hand");
        for(int i=0;i<3;i++) {
            int y=layout.centerY()-((selection.mask()&(1<<i))!=0?8:0);
            if(turn()&&selection.cards()&&cardFocus==i)
                g.renderOutline(layout.centerX(i)-layout.width()/2-2,y-layout.height()/2-2,layout.width()+4,layout.height()+4,0xFFFFD700);
            CardVisuals.card(g,hand.length==3?hand[i]:-1,layout.centerX(i),y,layout.width(),20+i*5);
            if((selection.mask()&(1<<i))!=0)
                g.renderOutline(layout.centerX(i)-layout.width()/2-1,y-layout.height()/2-1,layout.width()+2,layout.height()+2,0xFF55FF55);
        }
        centered(g,(me.getInt("revealed")==0?"暗牌":"明牌 "+me.getInt("revealed")+"/3")+" | 投入 "+me.getLong("paid")
            +" | 余额 "+(snapshot.data().getCompound("config").getInt("cap")-me.getLong("paid")),w/2,ZJHLayout.handInfoY(w,h),0xFFDDDDDD);
    }

    /** 选项行 + 底部提示，全部沿用斗地主那套"纯文字 + 金色焦点 + 绿色提示"；卡牌永远在最下面。 */
    private static void drawActions(GuiGraphics g,int w,int h) {
        if(snapshot.me()<0){centered(g,"只读观战 · Shift+右键牌桌停止",w/2,ZJHLayout.hintY(w,h),HudOptionBar.COLOR_HINT);return;}
        if(selection.cards()) {
            centered(g,"选牌模式 · 滚轮定位 · 左键选牌 · 右键牌桌翻牌 · 右键空气返回",w/2,ZJHLayout.hintY(w,h),HudOptionBar.COLOR_HINT);return;
        }
        var list=choices();if(list.isEmpty())return;
        drawOptionRow(g,w,list,ZJHLayout.optionsY(w,h));
        centered(g,hint(list),w/2,ZJHLayout.hintY(w,h),HudOptionBar.COLOR_HINT);
    }

    /**
     * 画一行选项：<b>等宽项 + 项内居中</b>，与斗地主叫分选项同一个 {@link HudOptionBar}。
     * 返回排布结果，供调用方对齐提示行文字。
     */
    private static HudOptionBar.Item[] drawOptionRow(GuiGraphics g,int w,List<Choice> list,int y) {
        var font=Minecraft.getInstance().font;
        int[] widths=new int[list.size()];
        for(int i=0;i<list.size();i++)widths[i]=font.width(list.get(i).label());
        var items=HudOptionBar.solve(w,widths,y);
        for(int i=0;i<items.length&&i<list.size();i++) {
            var item=items[i];var choice=list.get(i);
            boolean focused=i==Math.floorMod(actionFocus,list.size());
            if(focused)g.fill(item.focusLeft(),item.focusTop(),item.focusRight(),item.focusBottom(),HudOptionBar.COLOR_FOCUS_BACK);
            g.drawCenteredString(font,font.plainSubstrByWidth(choice.label(),item.textLimit()),
                item.centerX(),item.y(),HudOptionBar.colorOf(focused,choice.enabled()));
        }
        return items;
    }
    private static String hint(List<Choice> list) {
        if(busy())return "等待服务器确认…";
        if(snapshot.phase().equals("SETTLED"))return "已自动退局 · 右键任意位置关闭";
        if(choosingTarget)return "滚轮选择比牌目标 | 右键牌桌确认 | 右键空气返回";
        if(!snapshot.phase().equals("PLAYING"))return "滚轮选择 | 右键牌桌确认 | Shift+右键离开";
        if(snapshot.data().getInt("round")<3)return "滚轮选择 | 右键牌桌确认 · 第三轮开始可比牌";
        return "滚轮选择 | 右键牌桌确认"+(me().getInt("revealed")<3?" · 选明牌后选牌":"");
    }

    private static String chipName() {
        var location=net.minecraft.resources.ResourceLocation.tryParse(snapshot.data().getCompound("config").getString("chip"));
        return location==null?"":net.minecraft.core.registries.BuiltInRegistries.ITEM.get(location).getDescription().getString();
    }

    /** 结算只展示结果，服务端已自动退局。 */
    private static void result(GuiGraphics g,int w,int h) {
        var font=Minecraft.getInstance().font;
        HudConfetti.scrim(g,w,h);
        StringJoiner winners=new StringJoiner("、");
        boolean viewerWins=false;
        for(Tag e:snapshot.players())if(((CompoundTag)e).getBoolean("winner"))winners.add(((CompoundTag)e).getString("name"));
        if(snapshot.me()>=0&&snapshot.me()<snapshot.players().size())viewerWins=snapshot.players().getCompound(snapshot.me()).getBoolean("winner");
        if(viewerWins)HudConfetti.render(g,w,h);
        // 整块（标题 + 获胜行 + 玩家行）垂直居中，与斗地主同一套观感
        int row=ZJHLayout.resultRowHeight(h,snapshot.players().size());
        int top=ZJHLayout.resultTop(h,snapshot.players().size());
        int cardW=Math.max(7,Math.min(23,(int)((row-2)*CardGameHudLayout.MODEL_WIDTH/16)));
        centered(g,"本局结算",w/2,top,0xFFFFD700);
        centered(g,winners+" 获胜",w/2,top+14,0xFFFFD700);
        for(int i=0;i<snapshot.players().size();i++) {
            var p=snapshot.players().getCompound(i);int y=top+ZJHLayout.RESULT_HEADER_H+i*row;
            int color=p.getBoolean("winner")?0xFFFFD700:i==snapshot.me()?0xFF7FD4FF:0xFFCCCCCC;
            g.fill(10,y,w-10,y+row-2,p.getBoolean("winner")?0x554A3B0C:0x33333333);
            g.drawString(font,font.plainSubstrByWidth(p.getString("name"),Math.max(24,w/4-10)),14,y+row/2-4,color);
            int[] hand=p.getIntArray("hand");
            for(int c=0;c<hand.length;c++)CardVisuals.card(g,hand[c],w/3+c*(cardW+3),y+row/2,cardW,20+c*5);
            String type=hand.length==3&&Arrays.stream(hand).allMatch(c->c>=0)?ZJHHandEvaluator.evaluate(hand).type().label:"未公开";
            long net=p.getLong("payout")-p.getLong("paid");
            int x=Math.max(w/3+3*(cardW+3)+6,w*2/3);
            String headline=(p.getBoolean("winner")?"胜 ":"出局 ")+type;
            String netText="净得失 "+(net>=0?"+":"")+net;
            if(ZJHLayout.resultRowFitsTwoLines(row)) {
                g.drawString(font,font.plainSubstrByWidth(headline,w-x-10),x,y+row/2-9,color);
                g.drawString(font,font.plainSubstrByWidth(netText,w-x-10),x,y+row/2+1,color);
            } else {
                // 行高不足 22px（矮屏 + 多人）：硬叠两行会让第一行画进上一行里，改成一行并排。
                // 取舍：先把"净得失"这个前缀省掉，再把牌型裁短——**净得失的数字必须留在屏幕上**，
                // 它才是结算里唯一真正要看的信息（此前按整串裁，矮屏 6 人时正好把数字裁掉）。
                String tail=" "+(net>=0?"+":"")+net;
                String head=font.plainSubstrByWidth(headline,Math.max(12,w-x-10-font.width(tail)));
                g.drawString(font,head+tail,x,y+row/2-4,color);
            }
        }
        centered(g,"已自动结算并退局 · 右键任意位置关闭",w/2,ZJHLayout.resultHintY(h),HudOptionBar.COLOR_HINT);
    }

    private static void centered(GuiGraphics g,String text,int x,int y,int color) {
        var font=Minecraft.getInstance().font;
        var window=Minecraft.getInstance().getWindow();
        text=font.plainSubstrByWidth(text,Math.max(24,CardGameHudLayout.viewport(window.getGuiScaledWidth(),window.getGuiScaledHeight()).width()-16));
        HudText.centered(g,font,text,x,y,color);
    }
}
