package com.jokernan.craftycards.init;

import com.jokernan.craftycards.util.ItemHelper;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.client.renderer.item.ClampedItemPropertyFunction;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.LivingEntity;
import java.util.function.ToDoubleFunction;
import net.minecraft.resources.ResourceLocation;
import com.jokernan.craftycards.CCReference;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.ItemStack;

/**
 * 物品模型覆盖初始化 — 注册物品属性（Item Properties）以驱动模型变体切换。
 * <p>
 * 在客户端初始化阶段（{@link com.jokernan.craftycards.CraftyCards.ClientModEvents#onClientSetup}）调用。
 * 通过 {@link ItemProperties#register} 将物品 NBT/damage 数据映射为模型 JSON 中的
 * {@code "minecraft:custom_model_data"} 或 {@code "crafty_cards:xxx"} 属性值，
 * 使同一物品 ID 能根据数据显示不同外观。
 * </p>
 *
 * <h3>注册的属性</h3>
 * <ul>
 *   <li>{@code card} 物品：{@code crafty_cards:value} = damage 值（0-53，决定显示哪张牌面）</li>
 *   <li>{@code card_covered} 物品：{@code crafty_cards:skin} = SkinID（决定牌背颜色）</li>
 *   <li>{@code card_deck} 物品：{@code crafty_cards:skin} = SkinID（决定牌堆外观）</li>
 * </ul>
 *
 * @see InitItems#CARD
 * @see InitItems#CARD_COVERED
 */
public class InitModelOverrides {
    /**
     * 注册所有物品的模型覆盖属性。
     * <p>必须在客户端线程调用，且在资源加载完成后执行。</p>
     */
    public static void init() {
        // 卡牌：damage 值（0-53）映射为 value 属性，驱动 card.json 模型选择对应牌面
        ItemProperties.register(InitItems.CARD.get(), ResourceLocation.parse("crafty_cards:value"),
            discreteProperty(ItemStack::getDamageValue));
        // 覆盖牌：NBT 中的 SkinID 字节映射为 skin 属性，决定牌背颜色
        ItemProperties.register(InitItems.CARD_COVERED.get(), ResourceLocation.parse("crafty_cards:skin"),
            discreteProperty(stack -> ItemHelper.getNBT(stack).getByte("SkinID")));
        // 牌堆物品已下线，其 skin 属性一并移除（牌堆实体仍保留）
        // ItemProperties.register(InitItems.CARD_DECK.get(), ResourceLocation.parse("crafty_cards:skin"),
        //     (stack, world, player, seed) -> ItemHelper.getNBT(stack).getByte("SkinID"));
        CCReference.LOG.info("卡牌模型谓词已注册: card→crafty_cards:value(damage)、card_covered→crafty_cards:skin");
    }

    /** Card/skin IDs are discrete integers, not normalized tool properties.
     * Vanilla's default call clamps a lambda to [0,1]; override it on both loaders.
     * Keep JSON thresholds unchanged so existing resource packs remain compatible.
     */
    private static ClampedItemPropertyFunction discreteProperty(ToDoubleFunction<ItemStack> value) {
        return new ClampedItemPropertyFunction() {
            @Override public float unclampedCall(ItemStack stack, ClientLevel level, LivingEntity entity, int seed) {
                return (float) value.applyAsDouble(stack);
            }
            @Override public float call(ItemStack stack, ClientLevel level, LivingEntity entity, int seed) {
                return unclampedCall(stack, level, entity, seed);
            }
        };
    }

    /** 诊断是否已经打过（只打一次）。 */
    private static boolean probed;

    /** 问了几帧了（用来区分"还没烘焙好"和"模型压根不存在"）。 */
    private static int attempts;

    /** 等这么多帧还是 null，就认定不是时机问题而是模型真缺了。 */
    private static final int GIVE_UP_TICKS = 200;

    /**
     * 一次性诊断：**直接问游戏"不同牌面拿到的烘焙模型是不是同一个对象、谓词在不在"**。
     *
     * <p>为什么要这一条：原版在谓词查不到时**静默跳过**该 override（{@code ItemOverrides} 里
     * {@code getProperty(...) != null} 才用），没有任何日志；而"所有牌长得一样"在屏幕上
     * 既可能是谓词没生效、也可能是 damage 没设上——两种病因表现完全相同。</p>
     *
     * <p>调用时机必须在**资源加载之后**：{@code init()} 跑在模型烘焙之前，那时问不出东西
     * （实测启动后几帧内 {@code getModel} 返回 null，直接在 {@code getOverrides()} 上 NPE）。
     * 所以由各加载器的客户端 tick 反复调到第一次成功为止（标题界面就够，不需要进游戏）。</p>
     *
     * <p>等 {@value #GIVE_UP_TICKS} 帧仍是 null 就打一条警告并收工——"永远不打印"是没法判断的结果，
     * 必须让它自己说出"不是时机问题"。</p>
     */
    public static void probeOnce() {
        if (probed) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.getItemRenderer() == null) return; // 渲染器还没就绪，下一帧再问

        ItemStack a = new ItemStack(InitItems.CARD.get());
        a.setDamageValue(0);
        ItemStack b = new ItemStack(InitItems.CARD.get());
        b.setDamageValue(1);
        try {
            var modelA = mc.getItemRenderer().getModel(a, null, null, 0);
            var modelB = mc.getItemRenderer().getModel(b, null, null, 0);
            if (modelA == null || modelB == null) {
                retry("取到 null 模型");
                return;
            }
            probed = true;

            ResourceLocation key = ResourceLocation.parse("crafty_cards:value");
            boolean hasPredicate = ItemProperties.getProperty(a, key) != null;
            CCReference.LOG.info("卡牌模型诊断: damage(0)={} damage(1)={} 谓词存在={} 两个 damage 的模型是同一对象={}",
                a.getDamageValue(), b.getDamageValue(), hasPredicate, modelA == modelB);
        } catch (Throwable t) {
            // 资源重载没跑完时，原版 getModel 会**内部** NPE 在 bakedmodel.getOverrides() 上（实测），
            // 所以"太早"这条分支必须在 catch 里，不能只判 null 返回值——上一版就是栽在这里，
            // 异常被当成真故障、诊断一次就放弃，等于什么都没问出来。
            retry(t.toString());
        }
    }

    /** 还没准备好：继续等；等够了就认定"不是时机问题"，打一条能判断的告警收工。 */
    private static void retry(String why) {
        if (++attempts < GIVE_UP_TICKS) return;
        probed = true;
        CCReference.LOG.warn("卡牌模型诊断: 等了 {} 帧仍拿不到模型（{}）—— card 物品的模型没被加载出来，不是时机问题",
            GIVE_UP_TICKS, why);
    }
}
