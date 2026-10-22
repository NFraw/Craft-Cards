package com.jokernan.craftycards.blockentity;

import com.jokernan.craftycards.init.InitBlockEntityTypes;
import net.minecraft.core.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** Persistent per-table rules. A temporary session lock serializes editing; there is no host. */
public final class ZJHTableBlockEntity extends BlockEntity {
    public String chip="";
    public int ante=1, cap=40, minimum=3, timeout=600;
    public boolean chips=true;
    public boolean configured;
    public ZJHTableBlockEntity(BlockPos pos,BlockState state){super(InitBlockEntityTypes.ZJH_TABLE.get(),pos,state);}
    public CompoundTag config() {
        CompoundTag t=new CompoundTag();t.putString("chip",chip);t.putInt("ante",ante);t.putInt("cap",cap);
        t.putInt("minimum",minimum);t.putInt("timeout",timeout);t.putBoolean("chips",chips);return t;
    }
    public void configure(CompoundTag t) {
        int a=t.getInt("ante"),c=t.getInt("cap"),n=t.getInt("minimum"),time=t.getInt("timeout");
        if(a<1||a>10000||c<a||c>1000000||n<2||n>6||time<0||time>12000)
            throw new IllegalArgumentException("配置超出范围：人数2–6，底注1–10000，上限至少等于底注且不超过100万，限时0–600秒");
        if(t.getString("chip").length()>256)throw new IllegalArgumentException("筹码名称过长");
        ante=a;cap=c;minimum=n;timeout=time;chip=t.getString("chip");chips=t.getBoolean("chips");configured=true;setChanged();
    }
    @Override protected void saveAdditional(CompoundTag t,HolderLookup.Provider r) {
        super.saveAdditional(t,r);t.put("Rules",config());t.putBoolean("Configured",configured);
    }
    @Override protected void loadAdditional(CompoundTag t,HolderLookup.Provider r) {
        super.loadAdditional(t,r);
        if(t.contains("Rules"))try{configure(t.getCompound("Rules"));}catch(IllegalArgumentException ignored){}
        configured=t.contains("Configured")?t.getBoolean("Configured"):configured&&(!chip.isEmpty()||!chips);
    }
}
