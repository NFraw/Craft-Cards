package com.jokernan.craftycards.game;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 牌型播报语音的映射测试。
 *
 * <p>这块最容易错的是<b>点数换算</b>：引擎里 A=14、2=15、小王=16、大王=17，
 * 而语音素材用的是 1=A、2~10 为牌面、11=J、12=Q、13=K、14=小王、15=大王。
 * 两套编号一旦对错，表现是"出一张 A 却念 2"——声音有、但内容不对，很难察觉。</p>
 */
class CustomAudioVoiceTest {

    /** 点数换算：两套编号的对应关系逐条固定住。 */
    @Test
    void voiceRankMapping() {
        // 牌 id 编码：id/4+1 = 牌面（1=A, 2..10, 11=J, 12=Q, 13=K），id%4 = 花色
        assertEquals(1, CustomAudio.voiceRank(0), "牌面 A（id 0）→ 语音编号 1");
        assertEquals(2, CustomAudio.voiceRank(4), "牌面 2 → 语音编号 2");
        assertEquals(10, CustomAudio.voiceRank(36), "牌面 10 → 语音编号 10");
        assertEquals(11, CustomAudio.voiceRank(40), "牌面 J → 语音编号 11");
        assertEquals(12, CustomAudio.voiceRank(44), "牌面 Q → 语音编号 12");
        assertEquals(13, CustomAudio.voiceRank(48), "牌面 K → 语音编号 13");
        // 引擎里 2 比 K 大（rank 15），牌面上它是"2"，语音必须念 2 而不是 15
        assertTrue(DDZEngine.getRank(4) > DDZEngine.getRank(48), "引擎里 2 的 rank 应大于 K");
        // 王
        assertEquals(14, CustomAudio.voiceRank(52), "小王 → 语音编号 14");
        assertEquals(15, CustomAudio.voiceRank(53), "大王 → 语音编号 15");
    }

    /** 单张：按点数选 danN（含大小王）。 */
    @Test
    void singleUsesCardRank() {
        assertEquals("dan1", CustomAudio.voiceFor(DDZCardType.SINGLE, List.of(0)));      // A
        assertEquals("dan5", CustomAudio.voiceFor(DDZCardType.SINGLE, List.of(16)));     // 5
        assertEquals("dan13", CustomAudio.voiceFor(DDZCardType.SINGLE, List.of(48)));    // K
        assertEquals("dan14", CustomAudio.voiceFor(DDZCardType.SINGLE, List.of(52)));    // 小王
        assertEquals("dan15", CustomAudio.voiceFor(DDZCardType.SINGLE, List.of(53)));    // 大王
    }

    /** 对子 / 三条：按点数选 duiN / tupleN。 */
    @Test
    void pairAndTripleUseCardRank() {
        assertEquals("dui1", CustomAudio.voiceFor(DDZCardType.PAIR, List.of(0, 1)));       // 一对 A
        assertEquals("dui13", CustomAudio.voiceFor(DDZCardType.PAIR, List.of(48, 49)));    // 一对 K
        assertEquals("tuple7", CustomAudio.voiceFor(DDZCardType.TRIPLE, List.of(24, 25, 26)));  // 三个 7
    }

    /** 其余牌型各有一份固定语音。 */
    @Test
    void otherTypesMapToNamedVoices() {
        assertEquals("sandaiyi", CustomAudio.voiceFor(DDZCardType.TRIPLE_ONE, List.of(0, 1, 2, 8)));
        assertEquals("sandaiyi", CustomAudio.voiceFor(DDZCardType.TRIPLE_PAIR, List.of(0, 1, 2, 8, 9)));
        assertEquals("shunzi", CustomAudio.voiceFor(DDZCardType.STRAIGHT, List.of(20, 24, 28, 32, 36)));
        assertEquals("liandui", CustomAudio.voiceFor(DDZCardType.PAIR_STRAIGHT, List.of(20, 21, 24, 25, 28, 29)));
        assertEquals("feiji", CustomAudio.voiceFor(DDZCardType.PLANE, List.of(20, 21, 22, 24, 25, 26)));
        assertEquals("sidaier", CustomAudio.voiceFor(DDZCardType.FOUR_TWO, List.of(20, 21, 22, 23, 4, 8)));
        assertEquals("sidailiangdui", CustomAudio.voiceFor(DDZCardType.FOUR_TWO_PAIR, List.of(20, 21, 22, 23, 4, 5, 8, 9)));
        assertEquals("wangzha", CustomAudio.voiceFor(DDZCardType.ROCKET, List.of(52, 53)));
    }

    /** 炸弹不走语音映射（由可覆盖的 bomb 槽位负责），避免同一手牌两条播报路径。 */
    @Test
    void bombIsHandledByItsSlot() {
        assertNull(CustomAudio.voiceFor(DDZCardType.BOMB, List.of(20, 21, 22, 23)));
    }

    /** 无效/空输入不得抛异常。 */
    @Test
    void invalidInputReturnsNull() {
        assertNull(CustomAudio.voiceFor(null, List.of(0)));
        assertNull(CustomAudio.voiceFor(DDZCardType.SINGLE, List.of()));
        assertNull(CustomAudio.voiceFor(DDZCardType.SINGLE, null));
        assertNull(CustomAudio.voiceFor(DDZCardType.INVALID, List.of(0, 1)));
    }

    /** 语音清单本身要自洽：id 不重复、且覆盖映射会用到的全部编号。 */
    @Test
    void builtinVoiceListIsConsistent() {
        var voices = CustomAudio.VOICE_KEYS;
        assertEquals(voices.size(), voices.stream().distinct().count(), "语音 id 不应重复");
        for (int i = 1; i <= 15; i++) {
            assertTrue(voices.contains("dan" + i), "缺少单张语音 dan" + i);
        }
        for (int i = 1; i <= 13; i++) {
            assertTrue(voices.contains("dui" + i), "缺少对子语音 dui" + i);
            assertTrue(voices.contains("tuple" + i), "缺少三条语音 tuple" + i);
        }
        for (String named : List.of("sandaiyi", "shunzi", "liandui", "feiji", "sidaier",
                                    "sidailiangdui", "wangzha")) {
            assertTrue(voices.contains(named), "缺少牌型语音 " + named);
        }
        // 映射产出的每个 id 都必须在这份清单里（否则运行时会静默无声）
        assertEquals(48, voices.size(), "语音数量变了？同步 sounds/builtin/voices/ 下的文件");
    }
}
