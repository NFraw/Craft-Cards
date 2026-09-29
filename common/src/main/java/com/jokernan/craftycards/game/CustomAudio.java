package com.jokernan.craftycards.game;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 音频键与纯逻辑：不依赖 Minecraft，可直接单测。
 *
 * <p><b>模组不打包任何音频文件</b>——音效与 BGM 全部来自玩家安装的**音乐包**
 * （见 {@code client/SoundPack}）。本类定义"能被触发的音频键"，以及由键与候选文件
 * 生成资源包 {@code sounds.json} 的纯逻辑。</p>
 *
 * <h3>音频键</h3>
 * 一个键 = 游戏里一个可被触发的音效，共三类：
 * <ul>
 *   <li><b>音效槽位</b>（{@link Slot} 里非 BGM 的 7 个）：发牌、叫分、出牌、炸弹、过牌、胜负</li>
 *   <li><b>BGM 槽位</b>（{@link Slot} 里 BGM 的 6 个）：按牌局阶段与场上形势循环播放</li>
 *   <li><b>牌型语音</b>（{@link #VOICE_KEYS} 共 48 个）：出牌时按牌型与点数播报，如
 *       {@code dan1}（单张 A）、{@code shunzi}（顺子）</li>
 * </ul>
 *
 * <h3>一个键可以有多个文件</h3>
 * 每个键对应一组候选文件，各带**整数权重**。权重写进生成的 {@code sounds.json}
 * 的 {@code weight} 字段，由<b>原版音效引擎</b>在播放时按权重随机挑一个——
 * 所以"不要"这类音效可以准备多条随机播报，而模组侧不需要任何随机逻辑。
 *
 * <p>优先级（由 {@code ClientSounds} 实现）：牌型语音键有候选时用它，否则用通用槽位键，
 * 都没有则退回原版音效（BGM 则不播）。</p>
 */
public final class CustomAudio {
    private CustomAudio() {}

    /**
     * 音频槽位。
     *
     * <p>{@code key} 是配置与音乐包清单里的键名，也是注册到游戏里的音效 id
     * （BGM 加 {@code bgm_} 前缀避免与同名音效冲突）。三者一致，少一层要记的映射。</p>
     */
    public enum Slot {
        // === 音效 ===
        /** 开局发牌 / 洗牌。 */
        DEAL("deal", false, false),
        /** 叫分确认。 */
        BID("bid", false, false),
        /** 出牌（通用；未匹配到牌型语音时使用）。 */
        PLAY("play", false, false),
        /** 出牌（炸弹 / 火箭）。 */
        BOMB("bomb", false, false),
        /** 过牌（"不要"）。 */
        PASS("pass", false, false),
        /** 结算：自己获胜。 */
        WIN("win", false, false),
        /** 结算：自己失败。 */
        LOSE("lose", false, false),

        // === 背景音乐（循环播放；长音频用 stream 流式读取，不整段载入内存） ===
        /** 等待玩家加入。 */
        BGM_WAITING("bgm_waiting", true, true),
        /** 叫分 / 出牌进行中。 */
        BGM_PLAYING("bgm_playing", true, true),
        /**
         * 有人只剩三张以内的牌（{@link #CLUTCH_CARDS}）：对局进入"就要分出胜负"的紧张段。
         *
         * <p>它是"状态型"的：只要还有人在三张以内就一直用它。没配这首的音乐包会自动回退到
         * {@link #BGM_PLAYING}（见 {@link #bgmChain}），所以老包不会因为多了这个键而突然没背景音乐。</p>
         */
        BGM_CLUTCH("bgm_clutch", true, true),
        /**
         * 王炸（火箭）被打出来之后：高潮段。
         *
         * <p>判断依据是"桌面上最后一手是火箭"——斗地主里火箭无人能压，另外两家只能过牌，
         * 所以它会一直持续到下一轮重新出牌为止，正是"过后"该有的长度。</p>
         */
        BGM_ROCKET("bgm_rocket", true, true),
        /** 结算：自己获胜。 */
        BGM_WIN("bgm_win", true, true),
        /** 结算：自己失败。 */
        BGM_LOSE("bgm_lose", true, true);

        private final String key;
        private final boolean bgm;
        private final boolean stream;

        Slot(String key, boolean bgm, boolean stream) {
            this.key = key;
            this.bgm = bgm;
            this.stream = stream;
        }

        /** 配置 / 清单 / 音效 id 共用的键名。 */
        public String configKey() {
            return key;
        }

        /** 注册到游戏里的音效 id（与 {@link CustomAudio#soundId} 同源：槽位与键同名）。 */
        public String soundName() {
            return soundId(key);
        }

        /** 是否属于 BGM 分组。 */
        public boolean isBgm() {
            return bgm;
        }

        /** 是否需要流式播放（长音频用，避免整段载入内存）。 */
        public boolean stream() {
            return stream;
        }

        /** 按键名找槽位；不是槽位键时返回 {@code null}。 */
        public static Slot byKey(String key) {
            for (Slot slot : values()) {
                if (slot.key.equals(key)) return slot;
            }
            return null;
        }
    }

    /**
     * 牌型播报语音的键（共 48 个）。
     *
     * <p>命名沿用素材来源（huiming）的拼音：dan/dui/tuple 后接点数编号，
     * 其余按牌型名一份。点数编号见 {@link #voiceRank}。</p>
     */
    public static final List<String> VOICE_KEYS = buildVoiceKeys();

    private static List<String> buildVoiceKeys() {
        List<String> voices = new ArrayList<>();
        for (int i = 1; i <= 15; i++) voices.add("dan" + i);
        for (int i = 1; i <= 13; i++) voices.add("dui" + i);
        for (int i = 1; i <= 13; i++) voices.add("tuple" + i);
        voices.addAll(List.of("sandaiyi", "shunzi", "liandui", "feiji", "sidaier", "sidailiangdui", "wangzha"));
        return List.copyOf(voices);
    }

    /** 全部音频键（13 个槽位 + 48 条牌型语音），顺序固定，便于界面与生成结果稳定。 */
    public static List<String> allKeys() {
        List<String> keys = new ArrayList<>();
        for (Slot slot : Slot.values()) keys.add(slot.configKey());
        keys.addAll(VOICE_KEYS);
        return List.copyOf(keys);
    }

    /**
     * 音频键 → <b>注册到游戏里的音效事件名</b>（= 注入资源包 {@code sounds.json} 的顶层键）。
     *
     * <p>牌型语音带 {@code voice_} 前缀：48 条语音与 13 个槽位在同一张注册表里，
     * 前缀让"这是语音"一眼可辨，也避开将来加槽位时的意外撞名。槽位与键同名。</p>
     *
     * <p><b>注册（{@link com.jokernan.craftycards.init.InitSounds}）与生成资源包
     * （{@link #soundsJson}）必须都走这一个函数。</b> 曾经两边各写各的：注册用
     * {@code voice_<键>}，生成的 sounds.json 顶层键却写了裸键，于是 48 条语音
     * <b>全部没有声音</b>——而且不是"退回原版音效"：配置里这个键有候选，
     * {@code ClientSounds} 会照常播那个没有资源的事件，结果是纯静音。
     * 出牌语音正好走的就是这些键，用户看到的就是"出牌没声音"。</p>
     */
    public static String soundId(String key) {
        return VOICE_KEYS.contains(key) ? "voice_" + key : key;
    }

    /** 是否是合法的音频键（音乐包清单里出现未知键会被跳过并记日志）。 */
    public static boolean isKnownKey(String key) {
        return Slot.byKey(key) != null || VOICE_KEYS.contains(key);
    }

    /** 该键是否流式播放（BGM 为 true）。 */
    public static boolean isStream(String key) {
        Slot slot = Slot.byKey(key);
        return slot != null && slot.stream();
    }

    /**
     * 键的中文说明（界面与音乐包制作程序共用一套说法）。
     * 未知键返回键名本身。
     */
    public static String label(String key) {
        Slot slot = Slot.byKey(key);
        if (slot != null) {
            return switch (slot) {
                case DEAL -> "发牌 / 洗牌";
                case BID -> "叫分确认";
                case PLAY -> "出牌（通用）";
                case BOMB -> "炸弹 / 火箭";
                case PASS -> "过牌（不要）";
                case WIN -> "结算胜利";
                case LOSE -> "结算失败";
                case BGM_WAITING -> "BGM：等待玩家";
                case BGM_PLAYING -> "BGM：对局进行中";
                case BGM_CLUTCH -> "BGM：有人只剩三张牌";
                case BGM_ROCKET -> "BGM：王炸之后";
                case BGM_WIN -> "BGM：结算胜利";
                case BGM_LOSE -> "BGM：结算失败";
            };
        }
        if (key.startsWith("dan")) return "单张 " + rankLabel(key.substring(3));
        if (key.startsWith("dui")) return "对子 " + rankLabel(key.substring(3));
        if (key.startsWith("tuple")) return "三条 " + rankLabel(key.substring(5));
        return switch (key) {
            case "sandaiyi" -> "三带一 / 三带二";
            case "shunzi" -> "顺子";
            case "liandui" -> "连对";
            case "feiji" -> "飞机";
            case "sidaier" -> "四带二（单）";
            case "sidailiangdui" -> "四带二（对）";
            case "wangzha" -> "王炸 / 火箭";
            default -> key;
        };
    }

    /** 语音编号 → 牌面文字（1=A，2~10，11=J，12=Q，13=K，14=小王，15=大王）。 */
    private static String rankLabel(String number) {
        int n;
        try {
            n = Integer.parseInt(number);
        } catch (NumberFormatException e) {
            return number;
        }
        return switch (n) {
            case 1 -> "A";
            case 11 -> "J";
            case 12 -> "Q";
            case 13 -> "K";
            case 14 -> "小王";
            case 15 -> "大王";
            default -> String.valueOf(n);
        };
    }

    /**
     * 点数 → 语音编号：1=A，2~10 为牌面本身，11=J、12=Q、13=K，14=小王、15=大王。
     * <p>注意 {@link DDZEngine#getRank} 用的是 A=14、2=15、王=16/17，这里做一次换算。</p>
     */
    public static int voiceRank(int cardId) {
        return switch (DDZEngine.getRank(cardId)) {
            case 14 -> 1;    // A
            case 15 -> 2;    // 2
            case 16 -> 14;   // 小王
            case 17 -> 15;   // 大王
            default -> DDZEngine.getRank(cardId);   // 3..13
        };
    }

    /**
     * 本手牌对应的牌型语音键；没有对应语音时返回 {@code null}。
     *
     * <p>炸弹不在这里处理——它由通用的 {@code bomb} 槽位负责，避免同一手牌出现两条播报路径。
     * 火箭用"王炸"语音。</p>
     */
    public static String voiceFor(DDZCardType type, List<Integer> cards) {
        if (type == null || cards == null || cards.isEmpty()) return null;
        return switch (type) {
            case SINGLE -> "dan" + voiceRank(cards.get(0));
            case PAIR -> numbered("dui", cards);
            case TRIPLE -> numbered("tuple", cards);
            case TRIPLE_ONE, TRIPLE_PAIR -> "sandaiyi";   // 素材无"三带二"，退化为三带一
            case STRAIGHT -> "shunzi";
            case PAIR_STRAIGHT -> "liandui";
            case PLANE, PLANE_SINGLE, PLANE_PAIR -> "feiji";
            case FOUR_TWO -> "sidaier";
            case FOUR_TWO_PAIR -> "sidailiangdui";
            case ROCKET -> "wangzha";
            default -> null;                              // BOMB / INVALID 交给 bomb 槽位
        };
    }

    /** 对子 / 三条这类"同点数多张"的语音：取任意一张的点数编号。 */
    private static String numbered(String prefix, List<Integer> cards) {
        int rank = voiceRank(cards.get(0));
        return rank >= 1 && rank <= 13 ? prefix + rank : null;   // 王没有对子/三条语音
    }

    /**
     * "紧张段"的阈值：三家之中有人剩这么少的牌就算进入残局。
     *
     * <p>3 张是斗地主里最典型的临界点——地主或农民都只要再出一两手就能走完，
     * 也是玩家最需要听出来"对面要赢了"的时刻。</p>
     */
    public static final int CLUTCH_CARDS = 3;

    /**
     * 当前阶段该播哪首 BGM；返回 {@code null} 表示不播。
     *
     * <p>规则（{@link CustomAudioTest} 有钉子）：</p>
     * <ul>
     *   <li>等待中 → {@link Slot#BGM_WAITING}；叫分 → {@link Slot#BGM_PLAYING}；结算 → 胜负两首</li>
     *   <li>出牌阶段：桌面最后一手是<b>王炸</b> → {@link Slot#BGM_ROCKET}；
     *       否则有人剩 {@link #CLUTCH_CARDS} 张以内 → {@link Slot#BGM_CLUTCH}；否则进行中</li>
     *   <li>王炸优先于残局：两者同时成立时先给"刚炸完"的高潮，
     *       下一轮重新出牌后自然落回残局那首（火箭没人压得住，所以它不会被别的牌顶掉）</li>
     * </ul>
     *
     * <p>这只是"想要的"槽位；它在音乐包里没配文件时由 {@link #bgmChain} 往下回退，
     * 所以给老音乐包加这两个键不会让背景音乐突然消失。</p>
     *
     * @param phase              牌局阶段（{@code null} = 不在任何牌局里，不播）
     * @param viewerWins         结算阶段用：当前玩家是否获胜
     * @param minCardsLeft       出牌阶段用：三家剩余手牌的最小值（{@link #minCardsLeft}；
     *                           非出牌阶段传什么都行）
     * @param rocketOnTable      出牌阶段用：桌面最后一手是不是王炸
     */
    public static Slot bgmFor(DDZGamePhase phase, boolean viewerWins, int minCardsLeft, boolean rocketOnTable) {
        if (phase == null) return null;
        return switch (phase) {
            case WAITING -> Slot.BGM_WAITING;
            case BIDDING -> Slot.BGM_PLAYING;
            case PLAYING -> {
                if (rocketOnTable) yield Slot.BGM_ROCKET;
                yield minCardsLeft <= CLUTCH_CARDS ? Slot.BGM_CLUTCH : Slot.BGM_PLAYING;
            }
            case SETTLED -> viewerWins ? Slot.BGM_WIN : Slot.BGM_LOSE;
        };
    }

    /**
     * 三家剩余手牌的最小值——{@code 0}（空座位 / 已经出完的人）不算，一张都没有时返回
     * {@link Integer#MAX_VALUE}。
     *
     * <p>为什么要把 0 排除：快照里的空座位就是 0，若照单全收，"有人已经出完了"
     * （那一刻其实是结算）与"空座位"都会被当成"快出完了"，紧张段 BGM 会在
     * 等人/旁观的时候莫名其妙响起来。</p>
     */
    public static int minCardsLeft(List<Integer> counts) {
        int min = Integer.MAX_VALUE;
        if (counts == null) return min;
        for (Integer count : counts) {
            if (count == null || count <= 0) continue;
            if (count < min) min = count;
        }
        return min;
    }

    /**
     * BGM 的回退链：主槽位在音乐包里没配文件时依次往下找，都没有就不播。
     *
     * <p>不加这条链的话，"给老音乐包加了新的 BGM 键"会变成<b>背景音乐消失</b>：
     * 播放侧的规则是"这个键没候选就什么都不播"。有了它，老包在王炸/残局时继续放
     * {@link Slot#BGM_PLAYING}，只有新包才会听到那两个新槽位。</p>
     */
    public static List<Slot> bgmChain(Slot desired) {
        if (desired == Slot.BGM_ROCKET) return List.of(Slot.BGM_ROCKET, Slot.BGM_CLUTCH, Slot.BGM_PLAYING);
        if (desired == Slot.BGM_CLUTCH) return List.of(Slot.BGM_CLUTCH, Slot.BGM_PLAYING);
        return desired == null ? List.of() : List.of(desired);
    }

    /**
     * 文件名校验：只接受 {@code [A-Za-z0-9._-]+.ogg}。
     *
     * <p>两道考虑：一是防**路径穿越**（{@code ../} 之类会让模组去读不该读的文件）；
     * 二是这些名字会被写进生成的 {@code sounds.json}，收紧字符集同时避免了 JSON 注入。</p>
     */
    public static boolean isAcceptableFileName(String name) {
        if (name == null) return false;
        String trimmed = name.trim();
        if (!trimmed.endsWith(".ogg")) return false;
        if (trimmed.length() <= 4) return false;   // 只有 ".ogg" 不算
        for (int i = 0; i < trimmed.length(); i++) {
            char c = trimmed.charAt(i);
            boolean ok = (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9')
                || c == '.' || c == '_' || c == '-';
            if (!ok) return false;
        }
        // 排除以点开头的隐藏文件与 ".." 这类相对路径片段
        return !trimmed.startsWith(".");
    }

    // === 候选文件与资源包生成 ===

    /**
     * 一个候选音频文件：某个键下可选播的一个文件。
     *
     * @param ref      稳定标识（{@code pack:<包id>/<文件>} 或 {@code local:<文件>}），
     *                 配置里的权重覆盖按它记录，换包/换文件后仍然对得上
     * @param fileName 源文件名（界面显示用）
     * @param weight   播放权重（≥1）。权重进生成的 {@code sounds.json} 的 {@code weight}，
     *                 由原版引擎按权重随机挑一个
     * @param path     源文件绝对路径
     */
    public record Candidate(String ref, String fileName, int weight, Path path) {}

    /** 权重上限（界面用 ◀ ▶ 调，防止手滑填出一个吞掉其它候选的值）。 */
    public static final int MAX_WEIGHT = 99;

    /**
     * 生成注入资源包里的 {@code assets/crafty_cards/sounds.json}。
     *
     * <p>键 → 候选文件列表：每个键一个条目，条目里按权重列出该键的全部候选——
     * <b>由原版音效引擎按权重随机挑一个</b>，模组侧不做随机。此外为每个候选文件额外写一个
     * {@code preview/<键>_<序号>} 条目（指向同一个文件），供界面"试听某个具体文件"用：
     * 那个条目不会自动播放，只有 {@code SoundEvent.createVariableRangeEvent} 按这个 id 取。</p>
     *
     * <p>注意这份文件是**唯一**的 {@code sounds.json}（模组 jar 里不再带），
     * 所以只写有候选的键即可；没有候选的键保持无资源（音效退回原版、BGM 不播）。</p>
     */
    public static String soundsJson(Map<String, List<Candidate>> candidates) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\n");
        boolean firstEntry = true;
        for (Map.Entry<String, List<Candidate>> entry : candidates.entrySet()) {
            String key = entry.getKey();
            List<Candidate> list = entry.getValue();
            if (list == null || list.isEmpty()) continue;
            boolean stream = isStream(key);

            // 主条目：该键的全部候选（带权重，原版按权重随机挑）。
            // 顶层键必须是**注册用的音效 id**（语音是 voice_<键>），不是配置里的键名——
            // 两者混用会让整批语音静音（见 soundId 的说明）
            if (!firstEntry) sb.append(",\n");
            firstEntry = false;
            sb.append("  \"").append(soundId(key)).append("\": {\n    \"sounds\": [\n");
            for (int i = 0; i < list.size(); i++) {
                sb.append("      {\n");
                sb.append("        \"name\": \"crafty_cards:").append(audioFileName(key, i)).append("\",\n");
                sb.append("        \"stream\": ").append(stream).append(",\n");
                sb.append("        \"weight\": ").append(Math.max(1, list.get(i).weight())).append("\n");
                sb.append("      }").append(i == list.size() - 1 ? "\n" : ",\n");
            }
            sb.append("    ]\n  }");

            // 试听条目：一个候选一条，界面用它试听"某一个文件"
            for (int i = 0; i < list.size(); i++) {
                sb.append(",\n  \"").append(previewKey(key, i)).append("\": {\n");
                sb.append("    \"sounds\": [\n      {\n");
                sb.append("        \"name\": \"crafty_cards:").append(audioFileName(key, i)).append("\",\n");
                sb.append("        \"stream\": ").append(stream).append("\n");
                sb.append("      }\n    ]\n  }");
            }
        }
        sb.append("\n}\n");
        return sb.toString();
    }

    /** 候选文件在注入资源包里的路径（不带扩展名；一律用 键_序号 命名，避开大小写与重名问题）。 */
    public static String audioFileName(String key, int index) {
        return "custom/" + key + "_" + index;
    }

    /** 试听条目的键（id = {@code preview/<键>_<序号>}）。 */
    public static String previewKey(String key, int index) {
        return "preview/" + key + "_" + index;
    }

    /** 资源包的 {@code pack.mcmeta} 内容（1.21.1 的资源包格式为 34）。 */
    public static String packMcMeta(int resourcePackFormat) {
        return "{\n"
            + "  \"pack\": {\n"
            + "    \"pack_format\": " + resourcePackFormat + ",\n"
            + "    \"description\": \"Crafty Cards audio (generated from the music pack you selected)\"\n"
            + "  }\n"
            + "}\n";
    }

    /** 空候选表的便捷构造（生成与测试用）。 */
    public static Map<String, List<Candidate>> emptyTable() {
        return new LinkedHashMap<>();
    }
}
