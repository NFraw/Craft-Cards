package com.jokernan.craftycards.forge;

import com.jokernan.craftycards.CCReference;
import com.jokernan.craftycards.entity.data.CCDataSerializers;
import com.jokernan.craftycards.game.server.ServerGameConfig;
import com.jokernan.craftycards.init.*;
import com.jokernan.craftycards.platform.GamePaths;
import net.minecraft.network.syncher.EntityDataSerializer;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;

@Mod(CCReference.MOD_ID)
public final class CraftyCardsForge {
    public CraftyCardsForge() {
        // Forge 51 constructs mods without arguments; this entry works on 51 and 52.
        FMLJavaModLoadingContext context = FMLJavaModLoadingContext.get();
        GamePaths.setConfigDir(FMLPaths.CONFIGDIR.get());
        var bus = context.getModEventBus();
        ForgeItems.register(bus);
        ForgeEntityTypes.register(bus);
        ForgeBlockEntityTypes.register(bus);
        ForgeSounds.register(bus);
        DeferredRegister<EntityDataSerializer<?>> serializers =
            DeferredRegister.create(ForgeRegistries.Keys.ENTITY_DATA_SERIALIZERS, CCReference.MOD_ID);
        CCDataSerializers.STACK.bind(serializers.register("stack", CCDataSerializers::createStack));
        serializers.register(bus);
        bus.addListener(this::setup);
        ForgeTableEvents.register(MinecraftForge.EVENT_BUS);
        ForgeNetworking.register();
    }

    private void setup(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            ServerGameConfig.load();
        });
    }
}
