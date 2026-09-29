package com.jokernan.craftycards.client;

import com.jokernan.craftycards.CCReference;
import com.jokernan.craftycards.init.InitItems;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.ItemStack;

/**
 * 渲染用卡牌 ItemStack 缓存。
 *
 * <p>卡牌渲染每帧都要按牌面取一个 ItemStack。{@code new ItemStack} 会新建一份
 * {@code PatchedDataComponentMap}，随后的 {@code setDamageValue} 又会因写时复制再复制一次
 * 组件表；一帧几十张牌就是几十次纯重复的分配（17 张手牌的手牌区 + 结算界面三行 + 世界立牌）。
 * 卡牌 ID 只有 54 个、牌面完全由 damage 值决定，故按 ID 缓存复用。
 *
 * <p><b>只读约定</b>：缓存实例由各处渲染代码共享，调用方<b>不得修改</b>（改数量 / damage /
 * NBT），否则会污染后续所有帧的渲染。若将来需要按牌面之外的属性（如自定义皮肤 NBT）渲染，
 * 应改用"属性组合"做缓存键，而不是就地改缓存的 ItemStack。
 */
final class CardStacks {
    /** 单副牌张数（0-53，含大小王），与 {@code DDZEngine} 的卡牌编码一致。 */
    private static final int CARD_COUNT = 54;

    /** 牌面 ItemStack，索引 = 卡牌 ID；懒初始化。 */
    private static final ItemStack[] FACES = new ItemStack[CARD_COUNT];

    /** 牌背 ItemStack（世界立牌用，默认皮肤）。 */
    private static ItemStack covered;

    private CardStacks() {}

    /** 诊断是否打过（只打一次）。 */
    private static boolean probed;

    /** 问了几帧了（区分"还没烘焙好"和"真出不来"）。 */
    private static int attempts;

    /** 等这么多帧还没结果就收工告警。 */
    private static final int GIVE_UP_TICKS = 200;

    /**
     * 一次性诊断：**测 HUD 真正走的那条缓存路径**。
     *
     * <p>与 {@code InitModelOverrides.probeOnce()} 的区别很关键：那边测的是"现场新建的 ItemStack"，
     * 这边测的是 {@link #face(int)} 返回的**缓存实例**——HUD 手牌画的就是后者。
     * 两边表现可能不同（缓存、物品属性、clamp 都在这条路上）。</p>
     *
     * <p>打三件事：①物品的 maxDamage（damage 组件能存多大）；②damage 往返是否原样
     * （{@code setDamageValue(20)} 读回来还是 20 吗——被 clamp 过就会变小，那正好是
     * "所有牌长得一样"的成因）；③不同牌面解析出的烘焙模型是否互不相同。</p>
     */
    static void probeCachedOnce() {
        if (probed) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.getItemRenderer() == null) return;
        int[] ids = {0, 1, 2, 5, 20, 53};
        try {
            // 注意：资源重载没跑完时 getModel 会**内部** NPE（bakedmodel.getOverrides()），
            // 所以这一句必须在 try 里——放外面就等于让诊断自己崩掉、什么都问不出来。
            var model0 = mc.getItemRenderer().getModel(face(0), null, null, 0);
            if (model0 == null) return; // 还没烘焙好，下一帧再问

            StringBuilder roundTrip = new StringBuilder();
            boolean allSame = true;
            for (int id : ids) {
                ItemStack s = face(id);
                if (roundTrip.length() > 0) roundTrip.append(' ');
                roundTrip.append(id).append("->").append(s.getDamageValue());
                if (mc.getItemRenderer().getModel(s, null, null, 0) != model0) allSame = false;
            }
            probed = true;
            CCReference.LOG.info("卡牌缓存诊断: maxDamage={} damage 往返=[{}] 六张牌模型全相同={}",
                face(0).getMaxDamage(), roundTrip, allSame);
        } catch (Throwable t) {
            // 太早（资源还没加载完）不算失败：不置 probed，下一帧继续；等 200 帧还没结果才收工告警
            if (++attempts < GIVE_UP_TICKS) return;
            probed = true;
            CCReference.LOG.warn("卡牌缓存诊断: 等了 {} 帧仍拿不到模型（{}）——不是时机问题",
                GIVE_UP_TICKS, t.toString());
        }
    }

    /** 卡牌 ID → 牌面 ItemStack（damage 值即该 ID，驱动 card.json 的 54 个模型变体）。 */
    static ItemStack face(int cardId) {
        if (cardId < 0 || cardId >= CARD_COUNT) {
            // 正常牌局不会出现越界 ID（服务端引擎保证 0-53）；异常数据不缓存，避免污染缓存槽
            ItemStack stack = new ItemStack(InitItems.CARD.get());
            stack.setDamageValue(cardId);
            return stack;
        }
        ItemStack stack = FACES[cardId];
        if (stack == null) {
            stack = new ItemStack(InitItems.CARD.get());
            stack.setDamageValue(cardId);
            FACES[cardId] = stack;
        }
        return stack;
    }

    /** 牌背 ItemStack（牌面朝下，用于其他玩家的手牌立牌）。 */
    static ItemStack covered() {
        if (covered == null) {
            covered = new ItemStack(InitItems.CARD_COVERED.get());
        }
        return covered;
    }
}
