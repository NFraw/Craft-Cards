package com.jokernan.craftycards.game.server;

import com.jokernan.craftycards.game.ChipDenominations;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.*;
import java.util.*;

/** Shared physical currency for both games. Escrows keep base units; exchange happens only at inventory boundaries. */
public final class ChipCurrency {
    private ChipCurrency() {}
    private static Item blockFor(Item unit) {
        String id=ChipDenominations.blockFor(BuiltInRegistries.ITEM.getKey(unit).toString());
        return id==null?Items.AIR:BuiltInRegistries.ITEM.get(ResourceLocation.parse(id));
    }
    private static boolean plain(ItemStack stack,Item item) {
        return !stack.isEmpty()&&stack.is(item)&&ItemStack.isSameItemSameComponents(stack,new ItemStack(item));
    }
    private static long countExact(ServerPlayer player,Item item,boolean ordinaryOnly) {
        long result=0;var inv=player.getInventory();
        for(int i=0;i<inv.getContainerSize();i++) {
            var stack=inv.getItem(i);
            if(stack.is(item)&&(!ordinaryOnly||plain(stack,item)))result+=stack.getCount();
        }return result;
    }
    public static int count(ServerPlayer player,Item unit) {
        if(player==null||unit==null||unit==Items.AIR)return 0;
        Item block=blockFor(unit);
        long value=countExact(player,unit,false)+(block==Items.AIR?0:countExact(player,block,true)*9);
        return (int)Math.min(Integer.MAX_VALUE,value);
    }
    /** Validate the whole amount before removing anything; base items retain all their components. */
    public static List<ItemStack> take(ServerPlayer player,Item unit,int amount) {
        if(amount<0||count(player,unit)<amount)throw new IllegalArgumentException("筹码不足，需要 "+amount+" 个基础单位");
        if(amount==0)return new ArrayList<>();
        Item block=blockFor(unit);
        var plan=ChipDenominations.withdraw(countExact(player,unit,false),block==Items.AIR?0:countExact(player,block,true),amount);
        List<ItemStack> result=new ArrayList<>();
        remove(player,unit,(int)plan.units(),false,result);
        remove(player,block,(int)plan.blocks(),true,new ArrayList<>());
        append(result,unit,amount-plan.units());
        if(plan.change()>0)give(player,List.of(new ItemStack(unit,plan.change())));
        player.getInventory().setChanged();return result;
    }
    private static void remove(ServerPlayer player,Item item,int amount,boolean ordinaryOnly,List<ItemStack> result) {
        var inv=player.getInventory();
        for(int i=0;i<inv.getContainerSize()&&amount>0;i++) {
            ItemStack stack=inv.getItem(i);
            if(stack.is(item)&&(!ordinaryOnly||plain(stack,item))) {
                ItemStack part=stack.split(Math.min(amount,stack.getCount()));amount-=part.getCount();result.add(part);
            }
        }
        if(amount!=0)throw new IllegalStateException("筹码兑换期间背包内容发生变化");
    }
    private static void append(List<ItemStack> stacks,Item item,long count) {
        while(count>0) {int n=(int)Math.min(count,item.getDefaultMaxStackSize());stacks.add(new ItemStack(item,n));count-=n;}
    }
    /** Compact ordinary currency only. Custom names, enchantments and other components are never rewritten. */
    public static List<ItemStack> compact(List<ItemStack> input) {
        List<ItemStack> result=new ArrayList<>();Map<Item,Long> units=new LinkedHashMap<>();
        for(ItemStack stack:input) {
            if(stack.isEmpty())continue;
            if(blockFor(stack.getItem())!=Items.AIR&&plain(stack,stack.getItem()))
                units.merge(stack.getItem(),(long)stack.getCount(),Long::sum);
            else result.add(stack);
        }
        units.forEach((unit,n)->{append(result,blockFor(unit),n/9);append(result,unit,n%9);});
        return result;
    }
    public static void give(ServerPlayer player,List<ItemStack> stacks) {
        if(player==null)return;
        for(ItemStack stack:compact(stacks))if(!player.getInventory().add(stack)&&!stack.isEmpty())player.drop(stack,false);
        player.getInventory().setChanged();
    }
}
