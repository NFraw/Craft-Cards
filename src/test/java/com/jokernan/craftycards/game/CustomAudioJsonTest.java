package com.jokernan.craftycards.game;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * 生成的 sounds.json 的**结构**与**权重**校验。
 *
 * <p>补的是一个真实缺陷：手写 JSON 拼接时把收尾的 {@code }} 重复写了一次，于是
 * "条目写完就把根对象闭合了，后面的条目全跑到对象外面"。游戏侧的表现是整份
 * sounds.json 解析失败、**所有音频一起失效**——而只看"包不包含某个字符串"的断言对此完全无感
 * （条目文字确实都在）。所以这里用一个最朴素的括号深度扫描做结构校验：
 * 不引入 JSON 库（测试类路径上连 Gson 都没有），但足以抓住"根对象提前闭合""括号不配对"
 * "引号不成对"这几类拼接错误。</p>
 *
 * <p>另一件必须在这里钉住的事是**权重**：一个键下多个文件靠 {@code weight} 让原版引擎
 * 随机挑一个，权重写丢或写成 0 会让"多个文件随机播"直接退化成"只播第一个"。</p>
 */
class CustomAudioJsonTest {

    /** 一个候选：路径随便造一个（这里只测 JSON 文本生成，不读文件）。 */
    private static CustomAudio.Candidate candidate(String file, int weight) {
        return new CustomAudio.Candidate("pack:测试包/" + file, file, weight, Path.of(file));
    }

    private static Map<String, List<CustomAudio.Candidate>> table(Object... keyAndCandidates) {
        Map<String, List<CustomAudio.Candidate>> map = new LinkedHashMap<>();
        for (int i = 0; i < keyAndCandidates.length; i += 2) {
            map.put((String) keyAndCandidates[i], castList(keyAndCandidates[i + 1]));
        }
        return map;
    }

    @SuppressWarnings("unchecked")
    private static List<CustomAudio.Candidate> castList(Object o) {
        return (List<CustomAudio.Candidate>) o;
    }

    /** 单文件与多文件两种键，生成结果都必须是合法 JSON。 */
    @Test
    void jsonIsStructurallyValid() {
        assertStructurallyValid(CustomAudio.soundsJson(table(
            "pass", List.of(candidate("pass_1.ogg", 3), candidate("pass_2.ogg", 1)),
            "dan1", List.of(candidate("danA.ogg", 1)),
            "bgm_playing", List.of(candidate("normal.ogg", 2)))));
        // 空表也要是合法的 JSON 对象（否则游戏侧解析失败，所有音频失效）
        assertStructurallyValid(CustomAudio.soundsJson(Map.of()));
        assertStructurallyValid(CustomAudio.soundsJson(table("pass", List.of())));
    }

    /** 多文件：一个键的条目里要按顺序列出全部候选，各自带权重。 */
    @Test
    void multipleFilesWithWeightsAreListed() {
        String json = CustomAudio.soundsJson(table(
            "pass", List.of(candidate("pass_1.ogg", 3), candidate("pass_2.ogg", 7))));

        assertTrue(json.contains("\"pass\": {"), "缺少 pass 条目");
        assertTrue(json.contains("crafty_cards:custom/pass_0"), "第一个候选的文件名不对");
        assertTrue(json.contains("crafty_cards:custom/pass_1"), "第二个候选的文件名不对");
        assertEquals(1, countOccurrences(json, "\"weight\": 3"), "权重 3 没写进去");
        assertEquals(1, countOccurrences(json, "\"weight\": 7"), "权重 7 没写进去");
        // 两个候选在同一个 sounds 数组里（不是两条独立条目）
        assertEquals(1, countOccurrences(json, "\"pass\": {"));
    }

    /** 权重为 0/负数时按 1 处理——写成 0 会让原版永远挑不到它。 */
    @Test
    void zeroWeightIsClampedToOne() {
        String json = CustomAudio.soundsJson(table(
            "pass", List.of(candidate("a.ogg", 0), candidate("b.ogg", -5))));
        assertEquals(2, countOccurrences(json, "\"weight\": 1"), "权重必须被夹到 ≥1");
        assertFalse(json.contains("\"weight\": 0"), "不允许写出 0 权重");
    }

    /** 每个候选都要有试听条目，界面才能精确试听某一个文件。 */
    @Test
    void everyCandidateHasPreviewEntry() {
        String json = CustomAudio.soundsJson(table(
            "pass", List.of(candidate("a.ogg", 1), candidate("b.ogg", 1))));
        assertTrue(json.contains("\"preview/pass_0\":"), "缺少第一个候选的试听条目");
        assertTrue(json.contains("\"preview/pass_1\":"), "缺少第二个候选的试听条目");
    }

    /** BGM 要流式播放（否则长音频整段进内存），音效不流式。 */
    @Test
    void bgmStreamsAndEffectsDoNot() {
        String json = CustomAudio.soundsJson(table(
            "bgm_waiting", List.of(candidate("a.ogg", 1)),
            "pass", List.of(candidate("b.ogg", 1))));
        // bgm_waiting 的两处（主条目 + 试听条目）都应是 true
        assertEquals(2, countOccurrences(json, "\"stream\": true"), "BGM 应使用 stream");
        assertEquals(2, countOccurrences(json, "\"stream\": false"), "音效应整段载入");
    }

    /** 没有候选的键不该出现在 JSON 里（写了就会去播一个没有音频资源的事件）。 */
    @Test
    void keysWithoutCandidatesAreOmitted() {
        String json = CustomAudio.soundsJson(table("pass", List.of()));
        for (String key : CustomAudio.allKeys()) {
            assertFalse(json.contains("\"" + key + "\":"),
                "键 " + key + " 没有候选却写了条目");
        }
    }

    /** 键名出现次数：主条目 1 次 + 每个候选的试听条目 1 次，不允许重复条目。 */
    @Test
    void everyKeyAppearsExactlyOncePerEntry() {
        String json = CustomAudio.soundsJson(table(
            "pass", List.of(candidate("a.ogg", 1), candidate("b.ogg", 1))));
        assertEquals(1, countOccurrences(json, "\"pass\": {"), "pass 主条目应恰好 1 个");
        assertEquals(2, countOccurrences(json, "\"preview/pass_"), "试听条目数应等于候选数");
    }

    /**
     * 主条目的顶层键必须是**注册用的音效 id**（语音带 {@code voice_} 前缀），不是配置里的键名。
     *
     * <p>补的是一个真实缺陷：注册侧写 {@code voice_<键>}、这里写裸键，于是 48 条牌型语音在游戏里
     * 全部没有音频资源；而且不会退回原版音效——配置里"这个键有候选"，播放侧照播那个空事件，
     * 结果是纯静音（用户看到"出牌没声音"）。两边必须共用 {@link CustomAudio#soundId}。</p>
     */
    @Test
    void mainEntriesUseRegisteredSoundIds() {
        String json = CustomAudio.soundsJson(table(
            "dan1", List.of(candidate("a.ogg", 1)),
            "pass", List.of(candidate("b.ogg", 1))));

        assertTrue(json.contains("\"voice_dan1\": {"), "语音键必须用注册 id（voice_dan1）作条目名");
        assertFalse(json.contains("\"dan1\": {"), "不允许把配置键名当成音效 id");
        assertTrue(json.contains("\"pass\": {"), "槽位键与注册 id 同名");
        // 试听条目沿用配置键名（previewEvent 就是这么造 id 的），不要跟着加前缀
        assertTrue(json.contains("\"preview/dan1_0\":"), "试听条目应沿用配置键名");
    }

    /** 未知键（比如包清单里拼错的键）也会被如实生成条目 —— 过滤发生在读取包的时候。 */
    @Test
    void unknownKeysAreStillSyntacticallyValid() {
        assertStructurallyValid(CustomAudio.soundsJson(table(
            "这不是合法键", List.of(candidate("x.ogg", 1)))));
    }

    /**
     * 括号深度扫描：根对象一旦闭合，后面只允许空白。
     * 这正是 JSON 解析器报 "Extra data" 的那种错误。
     */
    private static void assertStructurallyValid(String json) {
        int depth = 0;
        boolean inString = false;
        boolean escaped = false;
        boolean rootClosed = false;

        for (int i = 0; i < json.length(); i++) {
            char c = json.charAt(i);

            if (inString) {
                if (escaped) {
                    escaped = false;
                } else if (c == '\\') {
                    escaped = true;
                } else if (c == '"') {
                    inString = false;
                }
                continue;
            }

            switch (c) {
                case '"' -> inString = true;
                case '{', '[' -> depth++;
                case '}', ']' -> depth--;
                default -> { }
            }
            assertTrue(depth >= 0, "括号不配对（第 " + i + " 个字符处提前闭合）");

            if (depth == 0 && (c == '}' || c == ']')) {
                rootClosed = true;
            } else if (rootClosed && !Character.isWhitespace(c)) {
                fail("根对象已闭合却仍有内容（第 " + i + " 个字符：「" + c + "」）——"
                    + "JSON 结构错误，游戏侧会导致整份 sounds.json 解析失败、所有音频失效");
            }
        }

        assertEquals(0, depth, "括号未闭合");
        assertFalse(inString, "字符串引号未配对");
        assertTrue(json.trim().startsWith("{") && json.trim().endsWith("}"), "应是一个 JSON 对象");
    }

    private static int countOccurrences(String text, String needle) {
        int count = 0;
        for (int from = text.indexOf(needle); from >= 0; from = text.indexOf(needle, from + needle.length())) {
            count++;
        }
        return count;
    }

    /** 主条目与试听条目的 id 不能撞车（撞了就会互相覆盖）。 */
    @Test
    void previewIdsDoNotCollideWithKeyIds() {
        List<String> ids = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            ids.add(CustomAudio.audioFileName("pass", i));
            ids.add(CustomAudio.previewKey("pass", i));
        }
        assertEquals(ids.size(), new java.util.HashSet<>(ids).size(), "id 重复");
    }
}
