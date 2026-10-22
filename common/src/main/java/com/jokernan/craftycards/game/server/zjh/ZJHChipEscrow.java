package com.jokernan.craftycards.game.server.zjh;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.*;
import net.minecraft.world.level.saveddata.SavedData;
import com.jokernan.craftycards.game.server.ChipCurrency;
import java.util.*;

/** Base-unit stacks, persisted independently of the table chunk. Ordinary denominations exchange at inventory boundaries. */
public final class ZJHChipEscrow extends SavedData {
    private static final String NAME="crafty_cards_zjh_escrow";
    private static final class Account {
        UUID session,owner;
        List<ItemStack> reserve=new ArrayList<>(), paid=new ArrayList<>();
        Account(UUID s,UUID o){session=s;owner=o;}
    }
    private final List<Account> accounts=new ArrayList<>();
    private final Map<UUID,List<ItemStack>> pending=new HashMap<>();
    public static ZJHChipEscrow get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(new Factory<>(ZJHChipEscrow::new,ZJHChipEscrow::load,null),NAME);
    }
    private Account account(UUID session,UUID owner) {
        return accounts.stream().filter(a->a.session.equals(session)&&a.owner.equals(owner)).findFirst()
            .orElseThrow(()->new IllegalStateException("托管账户丢失"));
    }
    public static int count(ServerPlayer player,Item item) {
        return ChipCurrency.count(player,item);
    }
    public void deposit(UUID session,ServerPlayer player,Item item,int amount) {
        if(count(player,item)<amount)throw new IllegalArgumentException(player.getScoreboardName()+" 筹码不足，需要 "+amount+" 个");
        if(accounts.stream().anyMatch(a->a.session.equals(session)&&a.owner.equals(player.getUUID())))
            throw new IllegalStateException("重复托管");
        Account a=new Account(session,player.getUUID());a.reserve.addAll(ChipCurrency.take(player,item,amount));
        accounts.add(a);player.getInventory().setChanged();setDirty();
    }
    public void charge(UUID session,UUID owner,int amount) {
        Account a=account(session,owner);a.paid.addAll(take(a.reserve,amount));setDirty();
    }
    private static List<ItemStack> take(List<ItemStack> source,long amount) {
        long available=source.stream().mapToLong(ItemStack::getCount).sum();
        if(amount<0||amount>available)throw new IllegalStateException("托管余额不足");
        List<ItemStack> result=new ArrayList<>();
        while(amount>0) {
            ItemStack s=source.getFirst();ItemStack part=s.split((int)Math.min(amount,s.getCount()));
            amount-=part.getCount();result.add(part);if(s.isEmpty())source.removeFirst();
        }return result;
    }
    private void credit(UUID owner,List<ItemStack> stacks) {
        if(!stacks.isEmpty())pending.computeIfAbsent(owner,k->new ArrayList<>()).addAll(stacks);
    }
    public void refundReserve(UUID session,UUID owner) {
        Account a=account(session,owner);credit(owner,new ArrayList<>(a.reserve));a.reserve.clear();setDirty();
    }
    public void settle(UUID session,List<UUID> owners,long[] payouts) {
        List<Account> group=accounts.stream().filter(a->a.session.equals(session)).toList();
        long sum=group.stream().flatMap(a->a.paid.stream()).mapToLong(ItemStack::getCount).sum();
        if(sum!=Arrays.stream(payouts).sum())throw new IllegalStateException("结算不守恒");
        List<ItemStack> pot=new ArrayList<>();for(Account a:group)pot.addAll(a.paid);
        for(int i=0;i<owners.size();i++)credit(owners.get(i),take(pot,payouts[i]));
        for(Account a:group)credit(a.owner,a.reserve);
        accounts.removeAll(group);setDirty();
    }
    public void cancel(UUID session) {
        for(Account a:List.copyOf(accounts))if(a.session.equals(session)) {
            credit(a.owner,a.reserve);credit(a.owner,a.paid);accounts.remove(a);
        }setDirty();
    }
    /** On server start unfinished hands are cancelled, not replayed as half-finished games. */
    public void recover() {
        for(UUID session:accounts.stream().map(a->a.session).distinct().toList())cancel(session);
    }
    public void deliver(ServerPlayer player) {
        List<ItemStack> claims=pending.remove(player.getUUID());if(claims==null)return;
        ChipCurrency.give(player,claims);
        player.getInventory().setChanged();setDirty();
    }
    public long held(UUID session) {
        return accounts.stream().filter(a->a.session.equals(session))
            .flatMap(a->java.util.stream.Stream.concat(a.reserve.stream(),a.paid.stream())).mapToLong(ItemStack::getCount).sum();
    }
    @Override public CompoundTag save(CompoundTag tag,HolderLookup.Provider r) {
        ListTag all=new ListTag();
        for(Account a:accounts) {
            CompoundTag t=new CompoundTag();t.putUUID("session",a.session);t.putUUID("owner",a.owner);
            t.put("reserve",saveStacks(a.reserve,r));t.put("paid",saveStacks(a.paid,r));all.add(t);
        }tag.put("accounts",all);
        ListTag claims=new ListTag();pending.forEach((id,stacks)->{
            CompoundTag t=new CompoundTag();t.putUUID("owner",id);t.put("items",saveStacks(stacks,r));claims.add(t);
        });tag.put("pending",claims);return tag;
    }
    private static ListTag saveStacks(List<ItemStack> stacks,HolderLookup.Provider r) {
        ListTag list=new ListTag();for(ItemStack s:stacks)if(!s.isEmpty())list.add(s.save(r));return list;
    }
    private static List<ItemStack> readStacks(CompoundTag t,String key,HolderLookup.Provider r) {
        List<ItemStack> result=new ArrayList<>();for(Tag entry:t.getList(key,Tag.TAG_COMPOUND))
            ItemStack.parse(r,entry).ifPresent(result::add);return result;
    }
    public static ZJHChipEscrow load(CompoundTag tag,HolderLookup.Provider r) {
        ZJHChipEscrow e=new ZJHChipEscrow();
        for(Tag entry:tag.getList("accounts",Tag.TAG_COMPOUND)) {
            CompoundTag t=(CompoundTag)entry;Account a=new Account(t.getUUID("session"),t.getUUID("owner"));
            a.reserve=readStacks(t,"reserve",r);a.paid=readStacks(t,"paid",r);e.accounts.add(a);
        }
        for(Tag entry:tag.getList("pending",Tag.TAG_COMPOUND)) {
            CompoundTag t=(CompoundTag)entry;e.pending.put(t.getUUID("owner"),readStacks(t,"items",r));
        }return e;
    }
}
