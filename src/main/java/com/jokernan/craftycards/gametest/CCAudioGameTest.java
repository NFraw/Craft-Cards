package com.jokernan.craftycards.gametest;

import com.jokernan.craftycards.CCReference;
import com.jokernan.craftycards.game.CustomAudio;
import com.jokernan.craftycards.init.InitSounds;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * 音频键 ↔ 音效事件 id 的契约（注册侧）。
 *
 * <p>为什么要有这一条：注入资源包的 {@code sounds.json} 是按 <b>id</b> 配音频的，
 * 而配置/音乐包里用的是**键名**。两边一旦不一致，表现是"某些音效完全没有声音"——
 * 不报错、不崩溃，只是静默（而且不会退回原版音效：配置里"这个键有候选"，播放侧照播那个空事件）。
 * 真实踩过：注册写 {@code voice_<键>}、生成 sounds.json 写裸键，48 条牌型语音全军覆没。</p>
 *
 * <p>单测只能钉住 {@code CustomAudio.soundId} 这个纯函数；"注册表里真的是这个 id"只有跑起来才知道，
 * 所以这里在真实注册表上逐一核对（61 个键）。</p>
 */
@GameTestHolder(CCReference.MOD_ID)
@PrefixGameTestTemplate(false)
public class CCAudioGameTest {
    /** 空结构模板（用例不碰方块，只是需要一个跑起来的环境）。 */
    private static final String TEMPLATE = "ddz_table_place";

    /** 每个音频键注册出来的事件 id 必须等于 {@code crafty_cards:<CustomAudio.soundId(键)>}。 */
    @GameTest(template = TEMPLATE)
    public static void soundEventIdsMatchSoundId(GameTestHelper helper) {
        for (String key : CustomAudio.allKeys()) {
            SoundEvent event = InitSounds.eventFor(key);
            if (event == null) {
                helper.fail("音频键 " + key + " 没有注册对应的音效事件");
                return;
            }
            ResourceLocation expected = CCReference.location(CustomAudio.soundId(key));
            if (!expected.equals(event.getLocation())) {
                helper.fail("音频键 " + key + " 的事件 id 应为 " + expected + "，实际 "
                    + event.getLocation() + "——生成资源包用的是 soundId()，两边不一致就会静音");
                return;
            }
            if (!InitSounds.isRegistered(key)) {
                helper.fail("音频键 " + key + " 不在注册表里（isRegistered 为假）");
                return;
            }
        }
        helper.succeed();
    }
}
