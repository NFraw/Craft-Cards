package com.jokernan.craftycards.client;

import com.jokernan.craftycards.game.CustomAudio;
import com.jokernan.craftycards.game.DDZCardType;
import com.jokernan.craftycards.init.InitSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;

import java.util.List;

/**
 * 斗地主音效播放：语义事件 → 音频键。
 *
 * <p>两条来源，按"该音频键当前有没有候选文件"二选一：</p>
 * <ul>
 *   <li><b>有</b> → 播放模组注册的音效事件（音频由 {@link com.jokernan.craftycards.platform.SoundPackInjection 加载器的注入资源包} 从音乐包复制而来；
 *       一个键下可能有多个文件，<b>由原版引擎按权重随机挑一个</b>）</li>
 *   <li><b>没有</b> → 直接播放**原版音效**（下方常量），零授权风险、随游戏分发</li>
 * </ul>
 *
 * <p>所有播放都走玩家实体，归入原版的"玩家"声道，受玩家音量设置控制。</p>
 */
public final class ClientSounds {
    // === 默认（原版）音效：该键没有音频时用它 ===

    /** 结算：获胜。 */
    private static final SoundEvent VANILLA_WIN = SoundEvents.PLAYER_LEVELUP;
    /** 结算：失败。 */
    private static final SoundEvent VANILLA_LOSE = SoundEvents.VILLAGER_NO;
    /** 出牌（普通牌型）。 */
    private static final SoundEvent VANILLA_PLAY = SoundEvents.ITEM_PICKUP;
    /** 出牌（炸弹 / 火箭）。 */
    private static final SoundEvent VANILLA_BOMB = SoundEvents.GENERIC_EXPLODE.value();
    /** 开局发牌 / 洗牌。 */
    private static final SoundEvent VANILLA_DEAL = SoundEvents.ARMOR_EQUIP_LEATHER.value();
    /** 叫分确认。 */
    private static final SoundEvent VANILLA_BID = SoundEvents.NOTE_BLOCK_PLING.value();
    /** 过牌。 */
    private static final SoundEvent VANILLA_PASS = SoundEvents.UI_BUTTON_CLICK.value();

    private ClientSounds() {}

    /** 玩家自身播放一个音效；客户端未就绪时什么都不做。 */
    private static void play(SoundEvent sound, float volume, float pitch) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || sound == null) return;
        mc.player.playSound(sound, volume * CustomSoundConfig.volume(), pitch);
    }

    /**
     * 播放某个音频键：有音频就用它（多文件时由原版按权重随机挑），否则用给定的原版音效。
     *
     * <p>两条路径都乘配置音量，这样调音量对两种来源都生效，行为一致。</p>
     */
    private static void playKey(String key, SoundEvent fallback, float volume, float pitch) {
        SoundEvent event = hasCandidates(key) ? InitSounds.eventFor(key) : null;
        play(event != null && libraryHas(event) ? event : fallback, volume, pitch);
    }

    /**
     * "配置里有候选" —— 但那只说明音乐包里写了这个键，不保证资源包装上了它。
     */
    private static boolean hasCandidates(String key) {
        return CustomSoundConfig.hasCandidates(key);
    }

    /**
     * 当前加载的音效库里真的有这个事件吗？
     *
     * <p>为什么要问这一句：<b>事件注册了不等于它有音频</b>。配置里"这个键有候选文件"只说明音乐包里
     * 声明了它，若生成资源包时那个条目没能写进去（复制失败、键名与注册 id 不一致等），
     * 播放这个事件就是<b>纯静音</b>——用户只会看到"出牌没声音"，而完全看不出原因。
     * 加了这道检查，这种情况会退回原版音效（不静默），并打一条只出现一次的告警。</p>
     *
     * <p>真实教训：初始化那边曾经用 {@code voice_<键>} 注册，而生成 sounds.json 时写的是裸键，
     * 于是 48 条牌型语音全部走到这里——那时还没有这道检查，结果是静音。</p>
     */
    private static boolean libraryHas(SoundEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.getSoundManager() == null) return true;   // 客户端还没起来：交给原版去处理
        if (mc.getSoundManager().getSoundEvent(event.getLocation()) != null) return true;
        if (MISSING_LOGGED.add(event.getLocation().toString())) {
            com.jokernan.craftycards.CCReference.LOG.warn(
                "音效事件 {} 在当前资源包里没有音频，本次退回原版音效（音乐包缺条目，或与注册 id 不一致）",
                event.getLocation());
        }
        return false;
    }

    /** 已经为哪些事件打过"库里没有音频"的告警（避免每次出牌都刷一行）。 */
    private static final java.util.Set<String> MISSING_LOGGED = java.util.concurrent.ConcurrentHashMap.newKeySet();

    /** 结算音效：胜负不同。 */
    public static void settlement(boolean win) {
        if (win) {
            playKey(CustomAudio.Slot.WIN.configKey(), VANILLA_WIN, 1.0F, 1.0F);
        } else {
            playKey(CustomAudio.Slot.LOSE.configKey(), VANILLA_LOSE, 1.0F, 1.0F);
        }
    }

    /**
     * 出牌音效。优先级：
     * <ol>
     *   <li>有对应**牌型语音**（"三带一""顺子"…）→ 播报牌型（同一牌型配了多条时随机播一条）</li>
     *   <li>否则用通用的出牌 / 炸弹槽位音频</li>
     *   <li>都没有 → 原版音效</li>
     * </ol>
     *
     * <p>牌型语音优先是刻意的：它说的是这手牌的实际内容，比通用音效更贴切。
     * 想让某种牌型闭嘴（或者只想听通用出牌音效），在配置界面把该键的权重全设 0 即可——
     * 没有候选的键会自动落到下一档。</p>
     *
     * @param type  牌型（用于选语音键）
     * @param cards 出的牌
     * @param big   是否为炸弹 / 火箭
     */
    public static void cardPlayed(DDZCardType type, List<Integer> cards, boolean big) {
        String voiceKey = CustomAudio.voiceFor(type, cards);
        if (voiceKey != null && hasCandidates(voiceKey)) {
            SoundEvent event = InitSounds.eventFor(voiceKey);
            // libraryHas：注册了不等于有音频，缺了就往下走通用槽位/原版，别静默
            if (event != null && libraryHas(event)) {
                play(event, 0.8F, 1.0F);
                return;
            }
        }
        CustomAudio.Slot slot = big ? CustomAudio.Slot.BOMB : CustomAudio.Slot.PLAY;
        playKey(slot.configKey(), big ? VANILLA_BOMB : VANILLA_PLAY, 0.6F, big ? 1.2F : 1.4F);
    }

    /** 开局发牌音效。 */
    public static void deal() {
        playKey(CustomAudio.Slot.DEAL.configKey(), VANILLA_DEAL, 0.7F, 1.0F);
    }

    /** 叫分确认音效。 */
    public static void bidConfirmed() {
        playKey(CustomAudio.Slot.BID.configKey(), VANILLA_BID, 0.8F, 1.4F);
    }

    /** 过牌音效。 */
    public static void passed() {
        playKey(CustomAudio.Slot.PASS.configKey(), VANILLA_PASS, 0.5F, 1.0F);
    }
}
