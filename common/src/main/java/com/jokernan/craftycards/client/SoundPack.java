package com.jokernan.craftycards.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.jokernan.craftycards.CCReference;
import com.jokernan.craftycards.game.CustomAudio;
import com.jokernan.craftycards.platform.GamePaths;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * 音乐包：玩家自建的音频包，放在 {@code config/crafty_cards/soundpacks/<包id>/}。
 *
 * <p><b>音乐包只由制作程序生成</b>（{@code tools/soundpack_maker.py}）——游戏内只读取它、
 * 选用它、调整各文件的**权重**（权重存在模组自己的配置里，不写回包包），
 * 所以永远不会出现"游戏改坏了包"这种事。</p>
 *
 * <p>组织逻辑（一个包 = 一个目录 + 一份清单）：</p>
 * <pre>
 * config/crafty_cards/soundpacks/官方默认包/
 *   pack.json     ← 清单：各音频键用哪些文件、各文件权重
 *   *.ogg         ← 音频文件（Ogg Vorbis）
 * </pre>
 *
 * <p>{@code pack.json}（版本 2）：</p>
 * <pre>
 * {
 *   "version": 2,
 *   "name": "官方默认包",
 *   "author": "作者",
 *   "description": "说明",
 *   "sounds": {
 *     "pass":   [ { "file": "pass_1.ogg", "weight": 3 }, "pass_2.ogg" ],
 *     "dan1":   [ "danA.ogg" ],
 *     "bgm_playing": [ "normal.ogg" ]
 *   }
 * }
 * </pre>
 *
 * <p>键是**音频键**（见 {@link CustomAudio#allKeys()}：7 个音效 + 6 个 BGM + 48 条牌型语音）；
 * 值是文件列表，元素可以是文件名字符串（权重 1）或 {@code {file, weight}}。
 * 权重是正整数，同一键下按权重随机播一个（由原版音效引擎完成）。
 * 没写的键没有音频，音效退回原版、BGM 按 {@link CustomAudio#bgmChain} 往下退（都没有才不播）。</p>
 */
public final class SoundPack {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    /** 清单文件名。 */
    public static final String MANIFEST = "pack.json";

    /** 清单里的一个条目：包内文件名 + 权重。 */
    public record Entry(String file, int weight) {}

    private final String id;
    private final String name;
    private final String author;
    private final String description;
    /** 音频键 → 该包为该键提供的文件。 */
    private final Map<String, List<Entry>> sounds;

    private SoundPack(String id, String name, String author, String description,
                      Map<String, List<Entry>> sounds) {
        this.id = id;
        this.name = name;
        this.author = author;
        this.description = description;
        this.sounds = sounds;
    }

    public String id() {
        return id;
    }

    public String name() {
        return name;
    }

    public String author() {
        return author;
    }

    public String description() {
        return description;
    }

    /** 该包为某个键提供的条目（不可变；未知键返回空列表）。 */
    public List<Entry> entriesFor(String key) {
        return sounds.getOrDefault(key, List.of());
    }

    /** 该包覆盖的键（界面显示"覆盖 N 个键"用）。 */
    public List<String> coveredKeys() {
        return List.copyOf(sounds.keySet());
    }

    /**
     * 该包为某个键提供的文件绝对路径；文件名非法或文件缺失时返回 {@code null}。
     *
     * <p>清单是玩家手工/工具生成的，路径穿越与拼错文件名都会在这里被挡掉——
     * 挡不住就会去读不该读的文件，或者生成一个指向空气的 {@code sounds.json} 条目。</p>
     */
    public Path pathFor(String key, String file) {
        if (!CustomAudio.isAcceptableFileName(file)) {
            CCReference.LOG.warn("音乐包 {} 的文件名不合法，已忽略：{}", id, file);
            return null;
        }
        Path p = root().resolve(id).resolve(file);
        if (!Files.isRegularFile(p)) {
            CCReference.LOG.warn("音乐包 {} 缺少文件，已忽略：{}", id, file);
            return null;
        }
        return p;
    }

    /** 音乐包根目录（供界面提示与制作程序写入）。 */
    public static Path root() {
        return GamePaths.config("soundpacks");
    }

    /** 扫描所有音乐包（按目录名排序；清单缺失/损坏的目录会被跳过并记一条日志）。 */
    public static List<SoundPack> scan() {
        if (!Files.isDirectory(root())) return List.of();
        List<SoundPack> packs = new ArrayList<>();
        try (Stream<Path> dirs = Files.list(root())) {
            for (Path dir : dirs.filter(Files::isDirectory).sorted().toList()) {
                Path manifest = dir.resolve(MANIFEST);
                if (!Files.isRegularFile(manifest)) {
                    CCReference.LOG.warn("音乐包 {} 缺少 {}，已跳过", dir.getFileName(), MANIFEST);
                    continue;
                }
                String id = dir.getFileName().toString();
                try {
                    SoundPack pack = parse(id, GSON.fromJson(Files.readString(manifest), Manifest.class));
                    if (pack != null) packs.add(pack);
                } catch (Exception e) {
                    CCReference.LOG.warn("音乐包 {} 的 {} 解析失败，已跳过：{}", id, MANIFEST, e.toString());
                }
            }
        } catch (IOException e) {
            CCReference.LOG.warn("扫描音乐包目录失败: {}", e.toString());
        }
        return packs;
    }

    private static SoundPack parse(String id, Manifest m) {
        if (m == null) return null;
        Map<String, List<Entry>> sounds = new LinkedHashMap<>();

        // 版本 2：sounds 里是"音频键 → 条目列表"
        if (m.sounds != null) {
            for (Map.Entry<String, List<Object>> e : m.sounds.entrySet()) {
                String key = e.getKey() == null ? "" : e.getKey().trim();
                if (!CustomAudio.isKnownKey(key)) {
                    CCReference.LOG.warn("音乐包 {} 出现未知音频键，已跳过：{}", id, key);
                    continue;
                }
                List<Entry> entries = new ArrayList<>();
                if (e.getValue() != null) {
                    for (Object raw : e.getValue()) {
                        Entry entry = toEntry(raw);
                        if (entry == null) continue;
                        if (hasFile(entries, entry.file())) {
                            CCReference.LOG.warn("音乐包 {} 的键 {} 里重复出现 {}，已忽略后一个",
                                id, key, entry.file());
                            continue;
                        }
                        entries.add(entry);
                    }
                }
                if (!entries.isEmpty()) sounds.put(key, List.copyOf(entries));
            }
        }

        // 版本 1（老包）：soundEffects / bgm 两个映射，一个槽位一个文件名
        addLegacy(sounds, m.soundEffects, false);
        addLegacy(sounds, m.bgm, true);

        String name = m.name == null || m.name.isBlank() ? id : m.name.trim();
        return new SoundPack(id, name, m.author == null ? "" : m.author,
            m.description == null ? "" : m.description, sounds);
    }

    /** 把清单里的一项（字符串或 {file,weight}）转成条目；非法项返回 null。 */
    private static Entry toEntry(Object raw) {
        if (raw == null) return null;
        if (raw instanceof String s) {
            String file = s.trim();
            return file.isEmpty() ? null : new Entry(file, 1);
        }
        if (raw instanceof Map<?, ?> map) {
            Object f = map.get("file");
            if (!(f instanceof String s) || s.isBlank()) return null;
            int weight = 1;
            Object w = map.get("weight");
            if (w instanceof Number n) weight = n.intValue();
            if (weight < 1) {
                CCReference.LOG.warn("权重 {} 不合法（必须 ≥1），按 1 处理：{}", weight, s);
                weight = 1;
            }
            return new Entry(s.trim(), Math.min(weight, CustomAudio.MAX_WEIGHT));
        }
        return null;
    }

    private static boolean hasFile(List<Entry> entries, String file) {
        for (Entry e : entries) {
            if (e.file().equals(file)) return true;
        }
        return false;
    }

    /** 兼容版本 1 的 manifest（{@code soundEffects}/{@code bgm} 两张表）。 */
    private static void addLegacy(Map<String, List<Entry>> out, Map<String, String> section, boolean bgm) {
        if (section == null) return;
        for (CustomAudio.Slot slot : CustomAudio.Slot.values()) {
            if (slot.isBgm() != bgm) continue;
            String file = section.get(slot.configKey());
            if (file == null || file.isBlank()) continue;
            out.putIfAbsent(slot.configKey(), List.of(new Entry(file.trim(), 1)));
        }
    }

    /** Gson 载体。{@code sounds} 的值用 {@code List<Object>} 接，兼容字符串与对象两种写法。 */
    private static class Manifest {
        int version = 2;
        String name;
        String author;
        String description;
        Map<String, List<Object>> sounds;
        // 版本 1 的字段（读到就转换，写包时不再产生）
        Map<String, String> soundEffects;
        Map<String, String> bgm;
    }
}
