package com.jokernan.craftycards.gametest;

import com.jokernan.craftycards.CCReference;
import com.jokernan.craftycards.game.server.ChipCurrency;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.*;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.*;
import net.neoforged.neoforge.gametest.*;
import java.util.*;

@GameTestHolder(CCReference.MOD_ID)
@PrefixGameTestTemplate(false)
public class ChipCurrencyGameTest {
    @GameTest(template="ddz_table_place") public static void exchangedEscrowSurvivesSaveAndRefundsOnce(GameTestHelper h) {
        var p=h.makeMockServerPlayerInLevel();
        try {
            p.getInventory().add(new ItemStack(Items.IRON_BLOCK,2));
            var escrow=new com.jokernan.craftycards.game.server.zjh.ZJHChipEscrow();var id=UUID.randomUUID();
            escrow.deposit(id,p,Items.IRON_INGOT,10);escrow.charge(id,p.getUUID(),3);
            h.assertTrue(escrow.held(id)==10&&DDZTestSupport.countOf(p,Items.IRON_INGOT)==8,"托管10单位，找零8单位");
            var restored=com.jokernan.craftycards.game.server.zjh.ZJHChipEscrow.load(escrow.save(new net.minecraft.nbt.CompoundTag(),h.getLevel().registryAccess()),h.getLevel().registryAccess());
            restored.recover();restored.recover();restored.deliver(p);restored.deliver(p);
            h.assertTrue(restored.held(id)==0&&DDZTestSupport.countOf(p,Items.IRON_INGOT)==18,"兑换后的恢复退款只发一次且价值守恒");h.succeed();
        }finally{h.getLevel().getServer().getPlayerList().remove(p);}
    }
    @GameTest(template="ddz_table_place") public static void ddzAcceptsBlocksAtEntryAndCollectsOnlyBaseStake(GameTestHelper h) {
        var core=new net.minecraft.core.BlockPos(3,1,3);
        h.setBlock(core,com.jokernan.craftycards.init.InitItems.DDZ_TABLE.get());
        var key=new com.jokernan.craftycards.network.payload.TableKey(h.getLevel().dimension(),h.absolutePos(core));
        var manager=com.jokernan.craftycards.game.server.DDZTableManager.getInstance();
        var players=new net.minecraft.server.level.ServerPlayer[3];
        try {
            for(int i=0;i<3;i++)players[i]=DDZTestSupport.atTable(h,core,h.makeMockServerPlayerInLevel());
            DDZTestSupport.prepareBetting(manager,key,players);
            for(var p:players){p.getInventory().clearContent();p.getInventory().add(new ItemStack(Items.DIAMOND_BLOCK,2));manager.join(p,key);}
            h.assertTrue(manager.session(key).playerCount()==3,"只带方块也应通过斗地主入局门槛");
            for(var p:players)h.assertTrue(DDZTestSupport.countOf(p,Items.DIAMOND)==17,"应只收1单位底注并找零");
            h.succeed();
        }finally{DDZTestSupport.cleanup(manager,key,h,players);}
    }
    @GameTest(template="ddz_table_place") public static void blocksChangeAndShortageAreValueConserving(GameTestHelper h) {
        var p=h.makeMockServerPlayerInLevel();
        try {
            for(Item[] family:new Item[][]{{Items.IRON_INGOT,Items.IRON_BLOCK},{Items.GOLD_INGOT,Items.GOLD_BLOCK},{Items.DIAMOND,Items.DIAMOND_BLOCK}}) {
                p.getInventory().clearContent();p.getInventory().add(new ItemStack(family[1],5));
                h.assertTrue(ChipCurrency.count(p,family[0])==45,"5块应折算45单位");
                var paid=ChipCurrency.take(p,family[0],40);
                h.assertTrue(paid.stream().mapToInt(ItemStack::getCount).sum()==40,"支付按基础单位入池");
                h.assertTrue(ChipCurrency.count(p,family[0])==5,"拆块后找零5");
                boolean rejected=false;try{ChipCurrency.take(p,family[0],6);}catch(IllegalArgumentException expected){rejected=true;}
                h.assertTrue(rejected&&ChipCurrency.count(p,family[0])==5,"不足时不能部分扣款");
                ChipCurrency.give(p,paid);h.assertTrue(ChipCurrency.count(p,family[0])==45,"退还后价值守恒");
            }h.succeed();
        }finally{h.getLevel().getServer().getPlayerList().remove(p);}
    }
    @GameTest(template="ddz_table_place") public static void customComponentsSurviveAndCustomBlocksAreNotBroken(GameTestHelper h) {
        var p=h.makeMockServerPlayerInLevel();
        try {
            var namedBlock=new ItemStack(Items.IRON_BLOCK);namedBlock.set(DataComponents.CUSTOM_NAME,Component.literal("纪念铁块"));
            p.getInventory().add(namedBlock);
            h.assertTrue(ChipCurrency.count(p,Items.IRON_INGOT)==0,"不得把带组件方块无损承诺为普通铁锭");
            var named=new ItemStack(Items.IRON_INGOT,9);named.set(DataComponents.CUSTOM_NAME,Component.literal("原物筹码"));
            p.getInventory().add(named);
            var paid=ChipCurrency.take(p,Items.IRON_INGOT,9);var packed=ChipCurrency.compact(paid);
            h.assertTrue(packed.size()==1&&packed.getFirst().is(Items.IRON_INGOT)&&packed.getFirst().getHoverName().getString().equals("原物筹码"),"自定义基础筹码不得压成白板方块");
            ChipCurrency.give(p,packed);h.assertTrue(ChipCurrency.count(p,Items.IRON_INGOT)==9,"自定义基础筹码可按原值收付");
            h.succeed();
        }finally{h.getLevel().getServer().getPlayerList().remove(p);}
    }
    @GameTest(template="ddz_table_place") public static void payoutCompactsOrdinaryCurrencyWithoutMixingMaterials(GameTestHelper h) {
        var packed=ChipCurrency.compact(List.of(new ItemStack(Items.IRON_INGOT,20),new ItemStack(Items.IRON_INGOT,8),new ItemStack(Items.DIAMOND,10)));
        h.assertTrue(packed.stream().filter(s->s.is(Items.IRON_BLOCK)).mapToInt(ItemStack::getCount).sum()==3,"28铁锭应打包3块");
        h.assertTrue(packed.stream().filter(s->s.is(Items.IRON_INGOT)).mapToInt(ItemStack::getCount).sum()==1,"铁锭余1");
        h.assertTrue(packed.stream().filter(s->s.is(Items.DIAMOND_BLOCK)).mapToInt(ItemStack::getCount).sum()==1,"钻石独立打包");h.succeed();
    }
}
