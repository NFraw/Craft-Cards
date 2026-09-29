package com.jokernan.craftycards;

import com.jokernan.craftycards.client.ClientBgm;
import com.jokernan.craftycards.client.ConfigHomeScreen;
import com.jokernan.craftycards.client.CustomSoundConfig;
import com.jokernan.craftycards.client.DDZGameHud;
import com.jokernan.craftycards.client.RenderConfig;
import com.jokernan.craftycards.client.RenderConfigScreen;
import com.jokernan.craftycards.client.ServerPlayConfigScreen;
import com.jokernan.craftycards.client.SoundConfigScreen;
import com.jokernan.craftycards.client.WorldHandCards;
import com.jokernan.craftycards.client.WorldPlayedCards;
import com.jokernan.craftycards.client.WorldTableChip;
import com.jokernan.craftycards.game.server.NeoForgeTableEvents;
import com.jokernan.craftycards.init.*;
import com.jokernan.craftycards.network.DDZNetworking;
import com.jokernan.craftycards.render.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.EntityRenderers;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.FMLPaths;

import com.jokernan.craftycards.platform.GamePaths;
import com.jokernan.craftycards.platform.Network;
import com.jokernan.craftycards.platform.WorldRenderHook;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.common.NeoForge;

/**
 * Crafty Cards 模组主入口类。
 * <p>
 * 被 {@code @Mod(CCReference.MOD_ID)} 注解标注，NeoForge 在模组加载时自动实例化此类，
 * 并将模组事件总线（modEventBus）和模组容器（modContainer）注入构造函数。
 * </p>
 *
 * <h3>构造函数职责</h3>
 * <ul>
 *   <li>注册生命周期事件监听器（{@link #commonSetup}）</li>
 *   <li>注册网络包类型（{@link DDZNetworking#registerPayloads}）</li>
 *   <li>注册服务端游戏事件（{@link NeoForgeTableEvents#register}）</li>
 *   <li>把各内容的注册绑定到模组事件总线（{@code init/NeoForge*} 各一个登记器）：
 *       方块、物品、创造标签页、实体类型、方块实体类型、配方序列化器、数据序列化器、音效——
 *       其中"有哪些内容、怎么构造"都在 common 的 {@code Init*} 类里</li>
 * </ul>
 *
 * <h3>架构说明</h3>
 * <p>本模组采用"HUD 叠加层 + 世界内渲染"模式，不使用全屏 Screen。
 * 客户端事件通过 {@link ClientGameEvents} 内部类手动注册到 GAME 事件总线
 * （生命周期事件显式绑定 MOD 总线，兼容 1.21 的加载器）。</p>
 *
 * @see CCReference#MOD_ID
 * @see InitItems
 * @see InitEntityTypes
 */
@Mod(CCReference.MOD_ID)
public class CraftyCards {
    /**
     * 模组构造函数 — NeoForge 在类加载时调用。
     *
     * @param modEventBus 模组事件总线，用于注册 DeferredRegister 和生命周期监听器
     * @param modContainer 模组容器，包含模组元数据（可选使用）
     */
    public CraftyCards(IEventBus modEventBus, ModContainer modContainer) {
        // 平台接缝：把配置目录交给 common/ 层（那边不知道"加载器是谁"，只知道一个目录）。
        // 必须**尽早**调用：下面这些注册表与配置类会在构造期/注册期就用到路径
        // （common/.../platform/GamePaths 里说明了为什么那些调用点改成惰性取值）
        GamePaths.setConfigDir(FMLPaths.CONFIGDIR.get());
        // 平台接缝：网络发送的实现（common 只负责"发什么"，这里决定"用什么管道发"）
        Network.install(new DDZNetworking.PacketDistributorBridge());

        // 注册通用生命周期事件（FMLCommonSetupEvent）
        modEventBus.addListener(this::commonSetup);
        // 音效事件槽位注册（模组不带音频文件；音乐包音频由客户端注入的资源包提供，
        // 没提供则音效走原版音效、BGM 不播——见 client/CustomSoundPack 与 ClientSounds）
        // 注意：键表与把手在 common 的 InitSounds 里，这里只做"登记"（多加载器接缝）
        NeoForgeSounds.register(modEventBus);

        // 注册网络包类型（C2S: PlayerActionPayload, S2C: GameStatePayload）
        modEventBus.addListener(DDZNetworking::registerPayloads);
        // 注册服务端 GAME 事件（玩家掉线、方块破坏、服务端 tick、右键牌桌）
        // 事件对象在 NeoForgeTableEvents 里解包，逻辑在 common 的 DDZTableManager
        NeoForgeTableEvents.register(NeoForge.EVENT_BUS);

        // === 内容登记（把手与构造逻辑都在 common/init，这里只做加载器侧的注册）===
        // 顺序：方块/物品 → 实体类型 → 方块实体类型（后两者构造时要取方块实例）
        NeoForgeItems.register(modEventBus);              // 方块 + 物品 + 创造标签页
        NeoForgeEntityTypes.register(modEventBus);        // 实体类型
        NeoForgeBlockEntityTypes.register(modEventBus);   // 方块实体类型
        NeoForgeDataSerializers.register(modEventBus);    // 实体数据序列化器
    }

    /**
     * FMLCommonSetupEvent 通用初始化回调。
     * <p>在所有模组注册完成后、世界加载前执行。当前仅输出日志确认模组已加载。</p>
     */
    private void commonSetup(final FMLCommonSetupEvent event) {
        // 服务端玩法配置（旁观可见性、旁观范围），双端加载、只有服务端读取
        com.jokernan.craftycards.game.server.ServerGameConfig.load();
        CCReference.LOG.info("Crafty Cards loaded!");
    }

    /**
     * 客户端专属事件（仅在 {@link Dist#CLIENT} 加载）。
     * <p>
     * 使用 {@code @EventBusSubscriber(modid, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)} 自动注册到 MOD 事件总线，
     * 处理客户端初始化（实体渲染器注册、模型覆盖、GUI 层注册）。
     * </p>
     */
    @EventBusSubscriber(modid = CCReference.MOD_ID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
    public static class ClientModEvents {
        /**
         * 客户端初始化：注册实体渲染器、模型覆盖、HUD 和世界内渲染。
         * <p>在 {@link FMLClientSetupEvent} 触发时执行，此时客户端资源已就绪。</p>
         */
        @SubscribeEvent
        public static void onClientSetup(FMLClientSetupEvent event) {
            // 注册卡牌/牌堆/筹码/骰子的物品模型覆盖（damage → 模型变体映射）
            InitModelOverrides.init();

            // 注册实体渲染器：将实体类映射到对应的渲染器类
            EntityRenderers.register(InitEntityTypes.CARD.get(), RenderEntityCard::new);
            // 牌堆渲染器必须保留：牌堆**物品**虽已下线，但实体类型仍在注册表里
            // （卡牌翻面逻辑会查找附近的牌堆实体），而"类型已注册、渲染器缺失"会让
            // 旧存档里已存在的牌堆实体在渲染时抛 NPE 并崩客户端。
            EntityRenderers.register(InitEntityTypes.CARD_DECK.get(), RenderEntityCardDeck::new);
            // 筹码/骰子/座椅：实体类型也已下线，渲染器一并不注册（与类型保持一致）
            // EntityRenderers.register(InitEntityTypes.POKER_CHIP.get(), RenderEntityPokerChip::new);
            // EntityRenderers.register(InitEntityTypes.DICE.get(), RenderEntityDice::new);
            // EntityRenderers.register(InitEntityTypes.SEAT.get(), RenderEntitySeat::new);

            // 加载渲染参数配置（config/crafty_cards/visual.json），可用 /craftycards reload 热更新
            RenderConfig.load();
            // 自定义音频配置（config/crafty_cards/sounds.json）；资源包注入在其后的
            // AddPackFindersEvent 里完成（见 CustomSoundPack），故这里先读一次
            CustomSoundConfig.ensureLoaded();
            // 让设置界面出现在「Mods 列表 → Crafty Cards → Config」按钮上
            // （多层级结构：主页按分类进入音频 / 玩法 / 渲染各页）
            ModList.get().getModContainerById(CCReference.MOD_ID).ifPresent(container ->
                container.registerExtensionPoint(IConfigScreenFactory.class,
                    (c, parent) -> new ConfigHomeScreen(parent)));

            // GAME bus 事件手动注册；生命周期注解显式指定 MOD 总线以兼容 1.21
            NeoForge.EVENT_BUS.addListener(ClientGameEvents::onMouseButton);   // 鼠标按键拦截
            NeoForge.EVENT_BUS.addListener(ClientGameEvents::onMouseScroll);   // 滚轮选牌/叫分
            NeoForge.EVENT_BUS.addListener(ClientGameEvents::onRegisterClientCommands); // 渲染参数重载命令

            // 世界内渲染：其他玩家座位前的牌背立牌 + 桌面中央出牌展示 + 桌面悬浮筹码。
            // 三个渲染器挂在 common 的渲染接缝上（它们已不认识加载器事件），
            // 事件解包在下面的 onRenderLevelStage 里做
            WorldRenderHook.register(WorldHandCards.getInstance()::render);
            WorldRenderHook.register(WorldPlayedCards.getInstance()::render);
            WorldRenderHook.register(WorldTableChip.getInstance()::render);
            NeoForge.EVENT_BUS.addListener(ClientGameEvents::onRenderLevelStage);
            // 背景音乐：按阶段循环播放音乐包里的 BGM。ClientBgm.tick() 刻意不带事件参数
            // （common 不认识加载器的 tick 事件类型），所以这里显式给出事件类
            NeoForge.EVENT_BUS.addListener(
                net.neoforged.neoforge.client.event.ClientTickEvent.Post.class, e -> ClientBgm.tick());
        }

        /**
         * 注册 HUD 叠加层（GUI Layer）。
         * <p>使用 {@code registerAboveAll} 确保斗地主 HUD 渲染在所有原生 GUI 层之上。</p>
         */
        @SubscribeEvent
        public static void onRegisterGuiLayers(RegisterGuiLayersEvent event) {
            event.registerAboveAll(
                CCReference.location("ddz_hud"),
                (guiGraphics, deltaTracker) -> {
                    DDZGameHud.getInstance().render(guiGraphics, deltaTracker);
                    // 3D 牌面是丢进 guiGraphics.bufferSource() 的批处理，必须在本层内刷掉：
                    // 不刷的话原版会一直拖到"菜单之后"才真正画出来，于是出牌那块牌面浮在暂停菜单
                    // 的虚化之上——手牌/底牌都被虚化、唯独它清晰。"(为什么它总在最上层)"就是这个原因。
                    // 模组自己的世界渲染器（WorldHandCards / WorldPlayedCards / WorldTableChip）
                    // 都显式 endBatch()，这里同理。
                    guiGraphics.flush();
                });
        }
    }

    /**
     * 客户端 GAME 事件总线事件处理。
     * <p>在 {@link ClientModEvents#onClientSetup} 中手动注册，处理鼠标交互和实体骑乘事件。</p>
     */
    public static class ClientGameEvents {
        /**
         * 鼠标按键事件：左键确认选牌（出牌阶段）、右键取消选中（准星不指牌桌时）。
         * <p>在 BIDDING/PLAYING 阶段拦截所有左键事件，防止误破坏方块。</p>
         */
        static void onMouseButton(InputEvent.MouseButton.Pre event) {
            // 拦不拦由 common 的 HUD 判断（那边不认识加载器的事件类型），这里只负责取消事件
            if (DDZGameHud.getInstance().handleMouseClick(event.getButton(), event.getAction())) {
                event.setCanceled(true);
            }
        }

        /**
         * 鼠标滚轮事件：出牌阶段移动手牌焦点、叫分阶段切换叫分选项。
         * <p>在 BIDDING/PLAYING 阶段取消原生滚轮事件，防止切换热键栏。</p>
         */
        static void onMouseScroll(InputEvent.MouseScrollingEvent event) {
            if (DDZGameHud.getInstance().handleScroll(event.getScrollDeltaY())) {
                event.setCanceled(true);
            }
        }

        /**
         * 渲染阶段：半透明方块之后 → 解包成 common 的 {@link WorldRenderHook.Context} 交给三个渲染器。
         *
         * <p>NeoForge 侧的 {@code AFTER_TRANSLUCENT_BLOCKS} 与原版 {@code renderSectionLayer}
         * 末尾那一次分发是同一个点；Fabric 侧用 mixin 复刻它（见
         * {@code fabric/.../mixin/LevelRendererMixin}），两边语义一致。</p>
         */
        static void onRenderLevelStage(RenderLevelStageEvent event) {
            if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return;
            WorldRenderHook.afterTranslucentBlocks(new WorldRenderHook.Context(
                event.getCamera(), event.getFrustum(), event.getRenderTick(), event.getPartialTick()));
        }

        /**
         * 注册客户端命令：{@code /craftycards reload} 重新读取渲染参数配置。
         * <p>
         * 客户端命令在客户端线程执行，可安全使用 {@link Minecraft}。
         * 渲染代码每帧读取 {@link RenderConfig} 的可变静态字段，
         * 因此 reload 后无需重启游戏，下一帧即生效。
         * </p>
         */
        static void onRegisterClientCommands(RegisterClientCommandsEvent event) {
            event.getDispatcher().register(
                Commands.literal("craftycards")
                    .then(Commands.literal("reload").executes(ctx -> {
                        RenderConfig.load();
                        Minecraft mc = Minecraft.getInstance();
                        if (mc.player != null) {
                            mc.player.displayClientMessage(
                                Component.literal("§a[Crafty Cards] 渲染配置已重载"), false);
                        }
                        return 1;
                    }))
                    // 设置主页（与「Mods → Crafty Cards → Config」是同一个界面）
                    .then(Commands.literal("config").executes(ctx -> {
                        Minecraft mc = Minecraft.getInstance();
                        mc.setScreen(new ConfigHomeScreen(mc.screen));
                        return 1;
                    }))
                    // 直接跳到音乐包页（音频的唯一入口；返回时回到设置主页）
                    .then(Commands.literal("sounds").executes(ctx -> {
                        Minecraft mc = Minecraft.getInstance();
                        ConfigHomeScreen home = new ConfigHomeScreen(mc.screen);
                        mc.setScreen(new SoundConfigScreen(home));
                        return 1;
                    }))
                    // 直接跳到玩法设置（服务端参数，房主可改）
                    .then(Commands.literal("play").executes(ctx -> {
                        Minecraft mc = Minecraft.getInstance();
                        mc.setScreen(new ServerPlayConfigScreen(new ConfigHomeScreen(mc.screen)));
                        return 1;
                    }))
                    // 直接跳到渲染参数
                    .then(Commands.literal("render").executes(ctx -> {
                        Minecraft mc = Minecraft.getInstance();
                        mc.setScreen(new RenderConfigScreen(new ConfigHomeScreen(mc.screen)));
                        return 1;
                    }))
            );
        }
    }
}
