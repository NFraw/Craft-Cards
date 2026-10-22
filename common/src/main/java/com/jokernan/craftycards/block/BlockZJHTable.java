package com.jokernan.craftycards.block;

import com.jokernan.craftycards.block.base.BlockBase;
import com.jokernan.craftycards.blockentity.ZJHTableBlockEntity;
import com.jokernan.craftycards.client.ZJHClient;
import com.jokernan.craftycards.game.server.zjh.ZJHTableManager;
import com.jokernan.craftycards.network.payload.TableKey;
import net.minecraft.core.BlockPos;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

public final class BlockZJHTable extends BlockBase implements EntityBlock {
    public BlockZJHTable(){super(Block.Properties.of().sound(SoundType.WOOD).strength(2));}
    @Override public BlockEntity newBlockEntity(BlockPos p,BlockState s){return new ZJHTableBlockEntity(p,s);}
    @Override protected InteractionResult useWithoutItem(BlockState s,Level l,BlockPos p,Player who,BlockHitResult hit) {
        if(l.isClientSide)ZJHClient.tableClicked(new TableKey(l.dimension(),p),who.isShiftKeyDown());
        return InteractionResult.SUCCESS;
    }
    @Override public void onRemove(BlockState s,Level l,BlockPos p,BlockState next,boolean moving) {
        if(!l.isClientSide&&!next.is(this))ZJHTableManager.getInstance().removed(l,p);
        super.onRemove(s,l,p,next,moving);
    }
}
