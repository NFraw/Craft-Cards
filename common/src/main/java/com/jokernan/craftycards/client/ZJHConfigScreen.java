package com.jokernan.craftycards.client;

import com.jokernan.craftycards.network.payload.ZJHActionPayload.Action;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.*;
import java.util.*;

/** Same responsive row/layout machinery as the existing table configuration. */
public final class ZJHConfigScreen extends ConfigScreenBase {
    private final CompoundTag working;
    public ZJHConfigScreen() {
        super(null,"炸金花 · 本桌配置","公共牌桌 · 编辑期间由一人临时锁定");
        working=ZJHClient.snapshot().data().getCompound("config").copy();
    }
    @Override protected void buildRows() {
        var s=ZJHClient.snapshot();
        list.header("筹码与开局");
        List<String> ids=new ArrayList<>(List.of(""));
        for(Tag e:s.data().getList("chipChoices",Tag.TAG_STRING))if(!ids.contains(e.getAsString()))ids.add(e.getAsString());
        if(!ids.contains(working.getString("chip")))ids.add(working.getString("chip"));
        list.row(choiceRow("筹码物品","候选由服务器提供",ids.toArray(String[]::new),()->working.getString("chip"),v->working.putString("chip",v),ZJHConfigScreen::label));
        list.row(toggleRow("使用物品筹码","关闭后仅计算虚拟分数",()->working.getBoolean("chips"),v->working.putBoolean("chips",v)));
        list.row(choiceRow("起局人数","加入即就绪，达到此人数自动开局",new Integer[]{2,3,4,5,6},()->working.getInt("minimum"),v->working.putInt("minimum",v),v->v+" 人"));
        list.row(choiceRow("底注","暗注档位自动为底注的1、2、4倍",withCurrent(new Integer[]{1,2,5,10,20,50,100},working.getInt("ante")),()->working.getInt("ante"),v->working.putInt("ante",v),v->v+" 个"));
        list.row(choiceRow("个人投入上限","开局暂存该预算，结束退回未下注部分",withCurrent(new Integer[]{10,20,40,80,100,200,400,1000,10000},working.getInt("cap")),()->working.getInt("cap"),v->working.putInt("cap",v),v->v+" 个"));
        list.row(choiceRow("行动限时","翻牌不重置倒计时；0为不限时",withCurrent(new Integer[]{0,200,400,600,1200,2400},working.getInt("timeout")),()->working.getInt("timeout"),v->working.putInt("timeout",v),v->v==0?"不限时":v/20+" 秒"));
        list.note("第三轮可比牌 · 结算展示所有玩家手牌");
        list.note("铁锭／金锭／钻石作筹码时，同材质普通方块自动按1:9兑换并找零");
        if(!s.data().getBoolean("chipsAllowed"))list.note("服务器筹码总闸门已关闭，本桌只计算虚拟分数");
    }
    private static Integer[] withCurrent(Integer[] values,int current) {
        var list=new ArrayList<>(Arrays.asList(values));if(!list.contains(current))list.add(current);return list.toArray(Integer[]::new);
    }
    private static String label(String id) {
        if(id.isEmpty())return "未选择";
        var loc=net.minecraft.resources.ResourceLocation.tryParse(id);
        return loc==null?id:net.minecraft.core.registries.BuiltInRegistries.ITEM.get(loc).getDescription().getString();
    }
    @Override protected void buildFooter(){addFooterButton("保存并关闭",()->{ZJHClient.saveConfig(working);status="等待服务器确认…";});}
    /** 与斗地主本桌配置页同一条页脚提示。 */
    @Override protected String hint(){return "保存并关闭后释放编辑锁 · 其他玩家可继续编辑";}
    @Override public void tick() {
        var s=ZJHClient.snapshot();
        if(s==null||!s.data().getBoolean("configOpen")){if(Minecraft.getInstance().screen==this)Minecraft.getInstance().setScreen(null);}
        else if(!s.data().getString("feedback").isEmpty())status=s.data().getString("feedback");
    }
    @Override public void onClose(){ZJHClient.send(Action.CLOSE,0,-1);super.onClose();}
}
