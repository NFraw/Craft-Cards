package com.jokernan.craftycards.fabric;

import com.jokernan.craftycards.CCReference;
import com.jokernan.craftycards.entity.data.CCDataSerializers;
import com.jokernan.craftycards.game.CustomAudio;
import com.jokernan.craftycards.init.InitBlockEntityTypes;
import com.jokernan.craftycards.init.InitEntityTypes;
import com.jokernan.craftycards.init.InitItems;
import com.jokernan.craftycards.init.InitSounds;
import com.jokernan.craftycards.platform.RegHolder;
import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Fabric 侧的**内容登记**：把 common 的 {@code Init*} 声明逐个注册进游戏注册表。
 *
 * <p>与 NeoForge 侧的差别只有"什么时候登记"：NeoForge 走 {@code DeferredRegister} + 注册事件，
 * 这里是模组初始化时直接 {@link Registry#register}，注册完把对象 {@code bindValue} 回把手
 * （见 {@code platform/RegHolder}）。**"有哪些内容、怎么构造"两边共用 common 里那一份。**</p>
 *
 * <p>顺序有讲究：**方块必须先于方块物品与方块实体类型**——后两者的构造要取方块实例
 * （Fabric 是立即注册，所以取到的就是真对象；NeoForge 那边则必须包在延迟 lambda 里）。</p>
 */
public final class FabricContent {
    private static final Logger LOG = LoggerFactory.getLogger(CCReference.MOD_ID);

    private FabricContent() {}

    /** 注册全部通用内容（客户端与服务端都要跑）。 */
    public static void register() {
        var stackSerializer = CCDataSerializers.createStack();
        EntityDataSerializers.registerSerializer(stackSerializer);
        CCDataSerializers.STACK.bindValue(stackSerializer);
        registerBlocksAndItems();
        registerEntityTypes();
        registerBlockEntityTypes();
        registerSounds();
        registerCreativeTab();
        selfCheck();
    }

    /**
     * 登记完的自检：有没有把手漏绑。
     *
     * <p>漏注册在运行时只表现为"用到时抛一句 IllegalStateException"，而它可能发生在很远的地方
     * （音效是"出牌时没声音"、方块是"放置时崩"）。这里一次性把话说完——NeoForge 侧由
     * {@code CCAudioGameTest} 遍历注册表兜着，Fabric 侧没有 GameTest，就靠这一步。</p>
     */
    private static void selfCheck() {
        List<RegHolder<?>> all = new ArrayList<>();
        all.addAll(List.of(
            InitItems.CASINO_CARPET_SPACE, InitItems.DDZ_TABLE,
            InitItems.CASINO_CARPET_SPACE_ITEM, InitItems.DDZ_TABLE_ITEM,
            InitItems.CARD_COVERED, InitItems.CARD, InitItems.DDZ_CARD));
        all.addAll(List.of(InitEntityTypes.CARD, InitEntityTypes.CARD_DECK));
        all.add(InitBlockEntityTypes.DDZ_TABLE);
        all.add(CCDataSerializers.STACK);
        all.addAll(InitSounds.holders().values());

        List<String> unbound = new ArrayList<>();
        for (RegHolder<?> holder : all) {
            if (!holder.isBound()) unbound.add(holder.path());
        }
        if (unbound.isEmpty()) {
            LOG.info("Crafty Cards (Fabric) 内容自检通过：{} 个把手全部已登记", all.size());
        } else {
            LOG.error("Crafty Cards (Fabric) 有 {} 个内容没登记上，用到时会抛异常：{}",
                unbound.size(), unbound);
        }
    }

    private static void registerBlocksAndItems() {
        // 方块（先）
        Block carpet = Registry.register(BuiltInRegistries.BLOCK, id("casino_carpet_space"),
            InitItems.createCasinoCarpet());
        InitItems.CASINO_CARPET_SPACE.bindValue(carpet);

        Block table = Registry.register(BuiltInRegistries.BLOCK, id("ddz_table"), InitItems.createDdzTable());
        InitItems.DDZ_TABLE.bindValue(table);

        // 物品（后）
        InitItems.CASINO_CARPET_SPACE_ITEM.bindValue(Registry.register(BuiltInRegistries.ITEM,
            id("casino_carpet_space"), InitItems.createBlockItem(carpet)));
        InitItems.DDZ_TABLE_ITEM.bindValue(Registry.register(BuiltInRegistries.ITEM,
            id("ddz_table"), InitItems.createBlockItem(table)));
        InitItems.CARD_COVERED.bindValue(Registry.register(BuiltInRegistries.ITEM,
            id("card_covered"), InitItems.createCardCovered()));
        InitItems.CARD.bindValue(Registry.register(BuiltInRegistries.ITEM,
            id("card"), InitItems.createCard()));
        InitItems.DDZ_CARD.bindValue(Registry.register(BuiltInRegistries.ITEM,
            id("ddz_card"), InitItems.createDdzCard()));
    }

    private static void registerEntityTypes() {
        InitEntityTypes.CARD.bindValue(Registry.register(BuiltInRegistries.ENTITY_TYPE,
            id("card"), InitEntityTypes.createCard()));
        InitEntityTypes.CARD_DECK.bindValue(Registry.register(BuiltInRegistries.ENTITY_TYPE,
            id("card_deck"), InitEntityTypes.createCardDeck()));
    }

    private static void registerBlockEntityTypes() {
        InitBlockEntityTypes.DDZ_TABLE.bindValue(Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE,
            id("ddz_table"), InitBlockEntityTypes.createDdzTable()));
    }

    /**
     * 音效事件：**键表来自 common**（{@link InitSounds#holders()}），注册 id 用
     * {@link CustomAudio#soundId} —— 与 NeoForge 侧、与生成资源包写进 sounds.json 的顶层键同源。
     *
     * <p>注意这里只登记事件，**没有音频资源**（模组不带音频文件）：玩家装了音乐包才有声音，
     * 没装则音效回退原版、BGM 不播。</p>
     */
    private static void registerSounds() {
        for (Map.Entry<String, RegHolder<SoundEvent>> entry : InitSounds.holders().entrySet()) {
            ResourceLocation soundId = CCReference.location(CustomAudio.soundId(entry.getKey()));
            entry.getValue().bindValue(Registry.register(BuiltInRegistries.SOUND_EVENT, soundId,
                SoundEvent.createVariableRangeEvent(soundId)));
        }
    }

    private static void registerCreativeTab() {
        CreativeModeTab tab = FabricItemGroup.builder()
            .icon(() -> new ItemStack(InitItems.CARD.get()))
            .displayItems((params, output) -> InitItems.fillCreativeTab(output))
            .title(Component.translatable(InitItems.creativeTabTranslationKey()))
            .build();
        ResourceLocation tabId = id(CCReference.MOD_ID);
        Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB, tabId, tab);

        // 自检：创造标签页"注册了但列表里看不见"这件事在游戏里没有任何提示，
        // 所以这里把判据打出来：注册表里有没有、id 对不对、条目几个。
        // 条目数由 fillCreativeTab 决定，与 NeoForge 侧是同一份 common 代码。
        // （不在启动期查翻译：这时语言文件还没加载，查了必然为 false，只会误导。）
        boolean inRegistry = BuiltInRegistries.CREATIVE_MODE_TAB.containsKey(tabId);
        int entries = InitItems.creativeTabItems().size();
        LOG.info("创造标签页: id={} 在注册表中={} 条目={} 标题键={}",
            tabId, inRegistry, entries, InitItems.creativeTabTranslationKey());
    }

    private static ResourceLocation id(String path) {
        return CCReference.location(path);
    }
}
