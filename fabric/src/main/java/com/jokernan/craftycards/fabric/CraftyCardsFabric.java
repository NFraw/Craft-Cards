package com.jokernan.craftycards.fabric;

import com.jokernan.craftycards.CCReference;
import com.jokernan.craftycards.game.server.ServerGameConfig;
import com.jokernan.craftycards.platform.GamePaths;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Fabric 端入口 —— <b>POC 第二步：让 common 层的内容在 Fabric 上真的注册出来</b>。
 *
 * <p>做的事：①把平台接缝所需的"环境信息"注入 common（配置目录）；②内容登记
 * （{@link FabricContent}：方块/物品/实体类型/方块实体类型/音效/创造标签页）；
 * ③网络（{@link FabricNetworking}）；④服务端事件胶水（{@link FabricServerEvents}）。
 * 内容、语义与逻辑都在 common 里，两边共用。</p>
 *
 * <p>客户端 HUD、输入与渲染、资源包注入见客户端入口和 client mixin。</p>
 */
public class CraftyCardsFabric implements ModInitializer {
    /** 模组 id：必须与 fabric.mod.json、NeoForge 的 mods.toml 一致。 */
    public static final String MOD_ID = CCReference.MOD_ID;

    private static final Logger LOG = LoggerFactory.getLogger(CCReference.MOD_ID);

    @Override
    public void onInitialize() {
        // 平台接缝：配置目录交给 common（那边不认识加载器，只知道一个目录）
        GamePaths.setConfigDir(FabricLoader.getInstance().getConfigDir());

        // 内容登记：声明与构造在 common，登记在这边
        FabricContent.register();

        // 网络：包类型 + C2S 接收 + 发送桥（common 的 Network.Bridge）
        FabricNetworking.registerCommon();

        // 服务端事件胶水：tick / 掉线 / 拆桌（逻辑在 common 的 DDZTableManager）
        FabricServerEvents.register();

        // 双端配置：server.json（筹码总闸门、每轮限时、各种距离）必须在世界加载前读一次。
        // 漏了这一步不会有任何报错——只是代码里的字面量默认值生效、玩家改的配置不生效，
        // 属于最难查的那类"改了没反应"（NeoForge 侧在 commonSetup 里做同一件事）
        ServerGameConfig.load();

        String loader = FabricLoader.getInstance().getModContainer("fabricloader")
            .map(container -> container.getMetadata().getVersion().getFriendlyString())
            .orElse("?");
        String mc = FabricLoader.getInstance().getModContainer("minecraft")
            .map(container -> container.getMetadata().getVersion().getFriendlyString())
            .orElse("?");
        LOG.info("Crafty Cards (Fabric) 已加载：Minecraft {} / Fabric Loader {}", mc, loader);
    }
}
