package com.jokernan.craftycards.fabric;

import com.jokernan.craftycards.CCReference;
import com.jokernan.craftycards.client.ClientBgm;
import com.jokernan.craftycards.client.ClientDDZData;
import com.jokernan.craftycards.client.CustomSoundConfig;
import com.jokernan.craftycards.client.DDZGameHud;
import com.jokernan.craftycards.client.RenderConfig;
import com.jokernan.craftycards.client.WorldHandCards;
import com.jokernan.craftycards.client.WorldPlayedCards;
import com.jokernan.craftycards.client.WorldTableChip;
import com.jokernan.craftycards.init.InitEntityTypes;
import com.jokernan.craftycards.init.InitModelOverrides;
import com.jokernan.craftycards.network.payload.GameStatePayload;
import com.jokernan.craftycards.platform.WorldRenderHook;
import com.jokernan.craftycards.render.RenderEntityCard;
import com.jokernan.craftycards.render.RenderEntityCardDeck;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import com.jokernan.craftycards.game.server.DDZTableManager;
import com.jokernan.craftycards.network.payload.TableKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Fabric 侧的**客户端入口**（fabric.mod.json 的 {@code client} 入口点）。
 *
 * <p>与 NeoForge 侧的 {@code CraftyCards} 里的客户端部分对应：
 * 收快照、客户端 tick 驱动 BGM，HUD 用 {@code HudRenderCallback}，鼠标输入由 mixin 接入。</p>
 *
 * <p>客户端专用的类（{@code ClientPlayNetworking}、{@code Minecraft}）只在客户端入口使用，
 * 专用服务器永远不会加载它——这也是 Fabric 侧"客户端逻辑必须收在客户端入口点"的规矩。</p>
 */
public class CraftyCardsFabricClient implements ClientModInitializer {
    private static final Logger LOG = LoggerFactory.getLogger(CCReference.MOD_ID);

    @Override
    public void onInitializeClient() {
        registerClientResources();
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> ClientDDZData.getInstance().reset());
        // Fabric invokes this before vanilla's sneak-with-item shortcut. Consume once,
        // send our C2S action, and prevent the held item from being used on the table.
        UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
            if (!level.isClientSide || player.isSpectator()
                || !DDZTableManager.shouldForceBlockUse(player, level.getBlockState(hit.getBlockPos())))
                return InteractionResult.PASS;
            if (hand == InteractionHand.MAIN_HAND)
                ClientDDZData.getInstance().onTableRightClicked(new TableKey(level.dimension(), hit.getBlockPos()), true);
            return InteractionResult.CONSUME;
        });

        // S2C：牌局快照 → 客户端缓存（与 NeoForge 的 ClientPayloadHandler 同一句调用）
        ClientPlayNetworking.registerGlobalReceiver(GameStatePayload.TYPE, (payload, context) ->
            context.client().execute(() -> ClientDDZData.getInstance().apply(payload)));

        // 客户端 tick：背景音乐按阶段/形势换曲（倒计时是"按需算"的，不需要 tick）；
        // 顺带调一次卡牌模型诊断——它必须在**资源加载之后**才问得出来（模型先烘焙好），
        // 而 InitModelOverrides.init() 跑在烘焙之前。标题界面就会打出来，不必进游戏。
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            ClientBgm.tick();
            InitModelOverrides.probeOnce();
            DDZGameHud.probeCachedCardsOnce();
        });

        // HUD 叠加层：手牌、叫分选项、倒计时、出牌展示、结算界面。
        // Fabric API 有正式的 HUD 回调，所以这一块**不需要 mixin**（与 NeoForge 的
        // RegisterGuiLayersEvent 只是入口不同，画的内容是 common 里同一份 DDZGameHud）
        HudRenderCallback.EVENT.register((guiGraphics, tickCounter) ->
            DDZGameHud.getInstance().render(guiGraphics, tickCounter));

        LOG.info("Crafty Cards (Fabric) 客户端已初始化：快照接收 + BGM tick + HUD");
    }

    /**
     * 客户端资源与配置的加载（与 NeoForge 侧 {@code ClientModEvents.onClientSetup} 对应）。
     *
     * <p>这几行漏掉都不会报错，但会静默地"配置不生效 / 牌面全一样"：</p>
     * <ul>
     *   <li>{@link InitModelOverrides#init()} —— 卡牌物品按 damage 切牌面；漏了则 HUD 里每张牌
     *       长得一样（同一物品 id、模型分支没注册）</li>
     *   <li>{@link RenderConfig#load()} —— HUD/世界渲染参数（visual.json）</li>
     *   <li>{@link CustomSoundConfig#ensureLoaded()} —— 音乐包选择，供注入包和音效播放共用</li>
     *   <li>实体渲染器 —— 实体卡牌/牌堆类型已注册，**渲染器不注册会在旧存档里崩客户端**
     *       （见 AGENTS"下线功能时要成对处理"）；新世界虽然不会遇到，但这是三行的保险</li>
     * </ul>
     */
    private static void registerClientResources() {
        InitModelOverrides.init();
        RenderConfig.load();
        CustomSoundConfig.ensureLoaded();
        EntityRendererRegistry.register(InitEntityTypes.CARD.get(), RenderEntityCard::new);
        EntityRendererRegistry.register(InitEntityTypes.CARD_DECK.get(), RenderEntityCardDeck::new);

        // 世界内渲染：他人身前立牌 / 桌面出牌展示 / 桌面筹码。
        // 触发点由 LevelRendererMixin 提供（复刻 NeoForge 的 AFTER_TRANSLUCENT_BLOCKS）
        WorldRenderHook.register(WorldHandCards.getInstance()::render);
        WorldRenderHook.register(WorldPlayedCards.getInstance()::render);
        WorldRenderHook.register(WorldTableChip.getInstance()::render);
    }
}
