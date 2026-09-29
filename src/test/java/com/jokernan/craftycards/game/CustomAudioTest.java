package com.jokernan.craftycards.game;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 自定义音频槽位的纯逻辑测试。
 *
 * <p>这里守三件容易出错、又不容易在游戏里发现的事：
 * ①阶段到 BGM 的映射（错了会在错误的时候放错音乐）；
 * ②文件名校验（错了要么放不出声、要么把 {@code ../} 这类名字带进生成的 JSON）；
 * ③槽位命名唯一（两个槽位撞名会让其中一个永远不生效）。</p>
 */
class CustomAudioTest {

    /** 阶段 → BGM 槽位（含"残局"与"王炸之后"两个新槽位）。 */
    @Test
    void bgmFollowsPhase() {
        assertEquals(CustomAudio.Slot.BGM_WAITING, CustomAudio.bgmFor(DDZGamePhase.WAITING, false, 17, false));
        assertEquals(CustomAudio.Slot.BGM_PLAYING, CustomAudio.bgmFor(DDZGamePhase.BIDDING, false, 17, false));
        assertEquals(CustomAudio.Slot.BGM_PLAYING, CustomAudio.bgmFor(DDZGamePhase.PLAYING, true, 17, false));
        // 结算阶段按自己的胜负分曲
        assertEquals(CustomAudio.Slot.BGM_WIN, CustomAudio.bgmFor(DDZGamePhase.SETTLED, true, 17, false));
        assertEquals(CustomAudio.Slot.BGM_LOSE, CustomAudio.bgmFor(DDZGamePhase.SETTLED, false, 17, false));
        // 不在任何牌局里 → 不播
        assertNull(CustomAudio.bgmFor(null, true, 17, false));
        // 结算阶段不受"残局/王炸"影响（免得结算时放对局音乐）
        assertEquals(CustomAudio.Slot.BGM_WIN, CustomAudio.bgmFor(DDZGamePhase.SETTLED, true, 1, true));
    }

    /** 有人只剩三张以内 → 残局 BGM；多一张就回到"进行中"。 */
    @Test
    void clutchBgmWhenSomeoneIsNearlyOut() {
        assertEquals(CustomAudio.Slot.BGM_CLUTCH,
            CustomAudio.bgmFor(DDZGamePhase.PLAYING, true, 3, false));
        assertEquals(CustomAudio.Slot.BGM_CLUTCH,
            CustomAudio.bgmFor(DDZGamePhase.PLAYING, true, 1, false));
        assertEquals(CustomAudio.Slot.BGM_PLAYING,
            CustomAudio.bgmFor(DDZGamePhase.PLAYING, true, 4, false));
        // 空座位/已出完的 0 不算"快出完了"，否则等人时也会响残局曲
        assertEquals(Integer.MAX_VALUE, CustomAudio.minCardsLeft(java.util.List.of(0, 0, 0)));
        assertEquals(Integer.MAX_VALUE, CustomAudio.minCardsLeft(java.util.List.of()));
        assertEquals(Integer.MAX_VALUE, CustomAudio.minCardsLeft(null));
        assertEquals(5, CustomAudio.minCardsLeft(java.util.List.of(17, 0, 5)));
        assertEquals(CustomAudio.Slot.BGM_PLAYING,
            CustomAudio.bgmFor(DDZGamePhase.PLAYING, true, CustomAudio.minCardsLeft(java.util.List.of(17, 0, 0)), false));
    }

    /**
     * 王炸之后 → 王炸 BGM，且**优先于残局**（两个条件同时成立时先给刚炸完的高潮）。
     *
     * <p>火箭没人压得住，所以这个状态会持续到下一轮重新出牌——"过后"该有的长度。</p>
     */
    @Test
    void rocketBgmWinsOverClutch() {
        assertEquals(CustomAudio.Slot.BGM_ROCKET,
            CustomAudio.bgmFor(DDZGamePhase.PLAYING, true, 17, true));
        assertEquals(CustomAudio.Slot.BGM_ROCKET,
            CustomAudio.bgmFor(DDZGamePhase.PLAYING, true, 2, true));
        // 叫分阶段即使桌面残留火箭（不该发生）也不放王炸曲
        assertEquals(CustomAudio.Slot.BGM_PLAYING,
            CustomAudio.bgmFor(DDZGamePhase.BIDDING, true, 17, true));
    }

    /**
     * BGM 回退链：新槽位没配文件时要退回"进行中"，绝不能变成没声音。
     *
     * <p>这是"给老音乐包加新键"的安全带：老包（只有 bgm_playing 等）在新规则下依然有背景音乐。</p>
     */
    @Test
    void bgmChainFallsBackToPlaying() {
        assertEquals(java.util.List.of(CustomAudio.Slot.BGM_ROCKET, CustomAudio.Slot.BGM_CLUTCH,
            CustomAudio.Slot.BGM_PLAYING), CustomAudio.bgmChain(CustomAudio.Slot.BGM_ROCKET));
        assertEquals(java.util.List.of(CustomAudio.Slot.BGM_CLUTCH, CustomAudio.Slot.BGM_PLAYING),
            CustomAudio.bgmChain(CustomAudio.Slot.BGM_CLUTCH));
        assertEquals(java.util.List.of(CustomAudio.Slot.BGM_WAITING),
            CustomAudio.bgmChain(CustomAudio.Slot.BGM_WAITING));
        // 不在牌局里 → 空链（什么都不播）
        assertTrue(CustomAudio.bgmChain(null).isEmpty());
        // 链上每个键都必须是 BGM 槽位（别把音效混进来）
        for (CustomAudio.Slot slot : CustomAudio.bgmChain(CustomAudio.Slot.BGM_ROCKET)) {
            assertTrue(slot.isBgm(), slot.configKey() + " 不是 BGM 槽位");
        }
    }

    /**
     * 配置键的契约：它同时是**音效 id**、**音频文件名（去掉扩展名）**、
     * 以及音乐包清单（pack.json）里的字段名。三者必须一致——
     * 否则玩家按文档命名文件、或用制作程序生成的包会静默不生效。
     */
    @Test
    void configKeysAreTheContractWithPlayersAndTools() {
        var expected = java.util.Map.ofEntries(
            java.util.Map.entry(CustomAudio.Slot.DEAL, "deal"),
            java.util.Map.entry(CustomAudio.Slot.BID, "bid"),
            java.util.Map.entry(CustomAudio.Slot.PLAY, "play"),
            java.util.Map.entry(CustomAudio.Slot.BOMB, "bomb"),
            java.util.Map.entry(CustomAudio.Slot.PASS, "pass"),
            java.util.Map.entry(CustomAudio.Slot.WIN, "win"),
            java.util.Map.entry(CustomAudio.Slot.LOSE, "lose"),
            java.util.Map.entry(CustomAudio.Slot.BGM_WAITING, "bgm_waiting"),
            java.util.Map.entry(CustomAudio.Slot.BGM_PLAYING, "bgm_playing"),
            java.util.Map.entry(CustomAudio.Slot.BGM_CLUTCH, "bgm_clutch"),
            java.util.Map.entry(CustomAudio.Slot.BGM_ROCKET, "bgm_rocket"),
            java.util.Map.entry(CustomAudio.Slot.BGM_WIN, "bgm_win"),
            java.util.Map.entry(CustomAudio.Slot.BGM_LOSE, "bgm_lose"));
        for (var entry : expected.entrySet()) {
            assertEquals(entry.getValue(), entry.getKey().configKey(),
                "配置键变了——需同步 tools/soundpack_maker.py、音乐包格式文档与官方默认包");
            assertEquals(entry.getValue(), entry.getKey().soundName(),
                "音效 id 应与配置键一致（少一层映射）");
        }
        assertEquals(expected.size(), CustomAudio.Slot.values().length, "槽位数变了？同步脚本与文档");
    }

    /** 每个阶段都必须有确定的目标（不能"漏一个阶段导致音乐莫名停掉"），且不能是空链。 */
    @Test
    void everyPhaseHasABgmDecision() {
        for (DDZGamePhase phase : DDZGamePhase.values()) {
            CustomAudio.Slot slot = CustomAudio.bgmFor(phase, true, 17, false);
            assertTrue(slot != null || phase == DDZGamePhase.WAITING,
                "阶段 " + phase + " 应有 BGM 目标");
            if (slot != null) {
                assertFalse(CustomAudio.bgmChain(slot).isEmpty(), "阶段 " + phase + " 的目标没有回退链");
            }
        }
    }

    /** 文件名白名单：能挡住路径穿越与注入，但不误伤正常名字。 */
    @Test
    void fileNameValidation() {
        // 正常
        assertTrue(CustomAudio.isAcceptableFileName("card_play.ogg"));
        assertTrue(CustomAudio.isAcceptableFileName("My-Sound_v2.ogg"));
        assertTrue(CustomAudio.isAcceptableFileName("bgm_02.ogg"));

        // 非法：路径穿越、错误扩展名、隐藏文件、空名
        assertFalse(CustomAudio.isAcceptableFileName("../secret.ogg"), "必须挡住路径穿越");
        assertFalse(CustomAudio.isAcceptableFileName("sub/dir.ogg"), "不接受带目录分隔符的名字");
        assertFalse(CustomAudio.isAcceptableFileName("..\\win.ogg"), "必须挡住反斜杠路径");
        assertFalse(CustomAudio.isAcceptableFileName("card_play.mp3"), "只接受 .ogg");
        assertFalse(CustomAudio.isAcceptableFileName("card_play"), "缺扩展名");
        assertFalse(CustomAudio.isAcceptableFileName(".ogg"), "只有扩展名不算");
        assertFalse(CustomAudio.isAcceptableFileName(".hidden.ogg"), "隐藏文件不接受");
        assertFalse(CustomAudio.isAcceptableFileName("有 空格.ogg"), "空格不在白名单内");
        assertFalse(CustomAudio.isAcceptableFileName("引号\".ogg"), "引号会破坏生成的 JSON");
        assertFalse(CustomAudio.isAcceptableFileName(null));
        assertFalse(CustomAudio.isAcceptableFileName(""));
    }

    /**
     * 音频键的中文说明必须覆盖到每一个键 —— 界面与制作程序都靠它显示，
     * 漏一个键玩家就只能看到一个英文 id，不知道那是什么声音。
     */
    @Test
    void everyKeyHasALabel() {
        for (String key : CustomAudio.allKeys()) {
            String label = CustomAudio.label(key);
            assertTrue(label != null && !label.isBlank(), "键 " + key + " 没有中文说明");
            assertFalse(label.equals(key), "键 " + key + " 的说明退化成了键名");
        }
        assertEquals(13 + 48, CustomAudio.allKeys().size(), "音频键总数变了？同步文档与制作程序");
    }

    /** 语音编号 → 中文点数的显示（"单张 A"这种），错了界面会显示成 dan1 看不懂。 */
    @Test
    void voiceLabelsUseCardFaces() {
        assertEquals("单张 A", CustomAudio.label("dan1"));
        assertEquals("单张 10", CustomAudio.label("dan10"));
        assertEquals("对子 K", CustomAudio.label("dui13"));
        assertEquals("三条 Q", CustomAudio.label("tuple12"));
        assertEquals("顺子", CustomAudio.label("shunzi"));
        assertEquals("王炸 / 火箭", CustomAudio.label("wangzha"));
    }

    /** 键合法性判断：制作程序与清单校验都靠它挡住拼错的键。 */
    @Test
    void knownKeyCheckIsStrict() {
        assertTrue(CustomAudio.isKnownKey("pass"));
        assertTrue(CustomAudio.isKnownKey("bgm_waiting"));
        assertTrue(CustomAudio.isKnownKey("dan1"));
        assertTrue(CustomAudio.isKnownKey("wangzha"));
        assertFalse(CustomAudio.isKnownKey("Pass"), "大小写敏感");
        assertFalse(CustomAudio.isKnownKey("dan16"), "没有 dan16（单张最大是大小王）");
        assertFalse(CustomAudio.isKnownKey("voice_pass"));
        assertFalse(CustomAudio.isKnownKey(""));
    }

    /** 只有 BGM 键是流式的（长音频不整段进内存）。 */
    @Test
    void onlyBgmKeysStream() {
        for (CustomAudio.Slot slot : CustomAudio.Slot.values()) {
            assertEquals(slot.isBgm(), CustomAudio.isStream(slot.configKey()),
                slot.configKey() + " 的流式标志不对");
        }
        assertFalse(CustomAudio.isStream("dan1"), "牌型语音不该流式");
    }

    /**
     * 模组资源里不许有任何音频文件 —— 这是"音频全部来自音乐包"的硬约束。
     *
     * <p>一旦有人把 .ogg 放回 {@code src/main/resources}，jar 就又变成"自带音频"了，
     * 与设计（以及随之而来的授权/责任划分）相悖，所以直接在单测里挡住。</p>
     */
    @Test
    void modResourcesContainNoAudio() throws Exception {
        java.nio.file.Path resources = java.nio.file.Path.of("common/src/main/resources");
        assertTrue(java.nio.file.Files.isDirectory(resources), "公共资源目录必须存在，不能跳过检查");
        try (var stream = java.nio.file.Files.walk(resources)) {
            var audio = stream.filter(java.nio.file.Files::isRegularFile)
                .filter(p -> p.getFileName().toString().toLowerCase(java.util.Locale.ROOT).endsWith(".ogg"))
                .toList();
            assertTrue(audio.isEmpty(),
                "模组资源里出现了音频文件（应放进音乐包，由制作程序分发）：" + audio);
        }
    }

    /** 资源包元信息里的格式版本必须写对，否则游戏会认为资源包不兼容而拒绝加载。 */
    @Test
    void packMetaCarriesFormat() {
        String meta = CustomAudio.packMcMeta(34);
        assertTrue(meta.contains("\"pack_format\": 34"), "格式版本写错会导致资源包被拒绝加载");
    }

    /**
     * 音频键 → **注册 id** 的映射：语音加 {@code voice_} 前缀、槽位同名，61 个键两两不撞。
     *
     * <p>补的是一个真实缺陷：注册侧写 {@code voice_<键>}、生成 {@code sounds.json} 侧写裸键，
     * 于是 48 条牌型语音在游戏里<b>全部没有音频资源</b>，而且不会退回原版音效
     * （配置里"这个键有候选"，照播那个空事件 → 纯静音）。用户看到的就是"出牌没声音"。
     * 两边现在共用 {@link CustomAudio#soundId}，这个用例把它的语义钉死。</p>
     */
    @Test
    void soundIdPrefixesVoicesOnly() {
        for (String voice : CustomAudio.VOICE_KEYS) {
            assertEquals("voice_" + voice, CustomAudio.soundId(voice),
                "语音键的注册 id 必须带 voice_ 前缀（生成 sounds.json 用的是同一个函数）");
        }
        for (CustomAudio.Slot slot : CustomAudio.Slot.values()) {
            assertEquals(slot.configKey(), CustomAudio.soundId(slot.configKey()),
                "槽位键与注册 id 同名");
        }
        var ids = new HashSet<String>();
        for (String key : CustomAudio.allKeys()) {
            assertTrue(ids.add(CustomAudio.soundId(key)), "注册 id 撞名: " + key);
        }
        assertEquals(CustomAudio.allKeys().size(), ids.size(), "键数与注册 id 数应一致");
    }

    /** 槽位命名唯一性：撞名会让其中一个永远不生效，而配置里看不出问题。 */
    @Test
    void slotNamesAreUnique() {
        var soundNames = new HashSet<String>();
        var effectKeys = new HashSet<String>();
        var bgmKeys = new HashSet<String>();
        for (CustomAudio.Slot slot : CustomAudio.Slot.values()) {
            assertTrue(soundNames.add(slot.soundName()),
                "音效 id 重复: " + slot.soundName());
            boolean added = slot.isBgm() ? bgmKeys.add(slot.configKey()) : effectKeys.add(slot.configKey());
            assertTrue(added, "同组内配置字段名重复: " + slot.configKey());
        }
        // 分组正确：BGM 槽位都在 bgm 组，音效槽位都在音效组
        List<CustomAudio.Slot> effects = new ArrayList<>();
        List<CustomAudio.Slot> bgms = new ArrayList<>();
        for (CustomAudio.Slot slot : CustomAudio.Slot.values()) {
            (slot.isBgm() ? bgms : effects).add(slot);
        }
        assertEquals(7, effects.size(), "音效槽位数变了？同步更新 README 与配置说明");
        assertEquals(6, bgms.size(), "BGM 槽位数变了？同步更新 README、docs/音乐包格式.md 与制作程序");
    }

    /**
     * 制作程序里的中文名必须与 {@link CustomAudio#label} 逐字一致。
     *
     * <p>{@code CustomAudio.label} 的注释写着"界面与音乐包制作程序共用一套说法"——
     * 两边是同一份文案的两个副本，所以这里直接读脚本比对。</p>
     *
     * <p>真踩过：{@code win}/{@code lose} 与 {@code bgm_win}/{@code bgm_lose} 在制作程序里
     * 被写成同一个中文名（都叫"结算胜利/失败"），键列表里就是两行一模一样的中文名，
     * 玩家分不清哪个键是哪个。两组唯一的写法差别就是 BGM 那组的「BGM：」前缀，
     * 所以这条断言逐字比对、不留余地。</p>
     */
    @Test
    void makerLabelsMatchGameLabels() throws Exception {
        java.nio.file.Path maker = java.nio.file.Path.of("tools/soundpack_maker.py");
        if (!java.nio.file.Files.isRegularFile(maker)) return;   // 只把脚本拷出去用时跳过
        String text = java.nio.file.Files.readString(maker);

        // 脚本里的字面表长这样：("bgm_win", "BGM：结算胜利")
        var declared = new java.util.LinkedHashMap<String, String>();
        var matcher = java.util.regex.Pattern
            .compile("\\(\\s*\"([a-z0-9_]+)\"\\s*,\\s*\"([^\"]+)\"\\s*\\)")
            .matcher(text);
        while (matcher.find()) declared.put(matcher.group(1), matcher.group(2));

        int checked = 0;
        for (String key : CustomAudio.allKeys()) {
            String inMaker = declared.get(key);
            if (inMaker == null) continue;   // dan1..dan15 之类的名字由脚本按编号拼出来
            assertEquals(CustomAudio.label(key), inMaker,
                "键 " + key + " 的中文名两边不一致——制作程序与游戏界面必须用同一套说法");
            checked++;
        }
        assertTrue(checked >= 13 + 7,
            "只在制作程序里比对到 " + checked + " 个字面中文名（应为 13 个槽位 + 7 条牌型语音）");
    }

    /** 子串出现次数（不用正则，避免 Java 21 的字符串模板预览限制）。 */
    private static int countOccurrences(String text, String needle) {
        int count = 0, from = 0;
        while ((from = text.indexOf(needle, from)) >= 0) {
            count++;
            from += needle.length();
        }
        return count;
    }
}
