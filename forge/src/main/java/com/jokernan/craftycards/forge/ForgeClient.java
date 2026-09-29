package com.jokernan.craftycards.forge;

import com.jokernan.craftycards.CCReference;
import com.jokernan.craftycards.client.*;
import com.jokernan.craftycards.init.*;
import com.jokernan.craftycards.network.payload.GameStatePayload;
import com.jokernan.craftycards.platform.WorldRenderHook;
import com.jokernan.craftycards.render.*;
import net.minecraft.client.renderer.entity.EntityRenderers;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

/** This class is loaded only on the physical client. */
@Mod.EventBusSubscriber(modid = CCReference.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class ForgeClient {
    public static void receive(GameStatePayload payload) {
        ClientDDZData.getInstance().apply(payload);
    }

    @SubscribeEvent
    public static void setup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            InitModelOverrides.init();
            RenderConfig.load();
            CustomSoundConfig.ensureLoaded();
            EntityRenderers.register(InitEntityTypes.CARD.get(), RenderEntityCard::new);
            EntityRenderers.register(InitEntityTypes.CARD_DECK.get(), RenderEntityCardDeck::new);
            WorldRenderHook.register(WorldHandCards.getInstance()::render);
            WorldRenderHook.register(WorldPlayedCards.getInstance()::render);
            WorldRenderHook.register(WorldTableChip.getInstance()::render);
        });
        MinecraftForge.EVENT_BUS.addListener((TickEvent.ClientTickEvent.Post tick) -> {
            ClientBgm.tick();
            InitModelOverrides.probeOnce();
            DDZGameHud.probeCachedCardsOnce();
        });
        MinecraftForge.EVENT_BUS.addListener((ClientPlayerNetworkEvent.LoggingOut logout) ->
            ClientDDZData.getInstance().reset());
    }
}
