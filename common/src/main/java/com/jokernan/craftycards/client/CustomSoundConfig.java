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

/**
 * 音频配置（客户端）：{@code config/crafty_cards/sounds.json}。
 *
 * <p>音频的<b>唯一来源是音乐包</b>（{@code config/crafty_cards/soundpacks/<包id>/}）——
 * 由制作程序生成，游戏内只读取、选用，不改写。曾经的"玩家自备单文件"（把 .ogg 放进
 * {@code config/crafty_cards/sounds/} 再在界面里指派给某个音频键）已整条路径删除：
 * 音频只有包这一个接入接口，改音频就改包。旧配置里残留的 {@code localFiles} / {@code weights}
 * 键会被静默忽略（Gson 不认识的键不解析），不再报错也不再生效。</p>
 *
 * <p>每个音频键（音效 / BGM / 牌型语音，见 {@link CustomAudio}）在包里可以有**多个**候选文件，
 * 各带权重（{@code pack.json} 的 {@code {file, weight}} 写法）；权重会写进生成的
 * {@code sounds.json}，由原版音效引擎按权重随机挑一个。</p>
 *
 * <p>配置结构（版本 3）：</p>
 * <pre>
 * {
 *   "enabled": true, "volume": 1.0, "activePack": "官方默认包",
 *   "bgmCategory": "players", "duckVanillaMusic": true, "vanillaMusicDuck": 0.1
 * }
 * </pre>
 *
 * <p>其中 {@code bgmCategory} / {@code duckVanillaMusic} / {@code vanillaMusicDuck} 是 BGM 的
 * 播放策略，界面上不再提供入口（只在这里手改）：默认 {@code players} 声道 + 压低原版音乐。</p>
 *
 * <p>改了配置需要重建资源包并重载（音频必须经资源包被游戏加载），
 * 这一步由界面上的「保存并应用」触发，见 {@link SoundEditor#commit()}。</p>
 */
public final class CustomSoundConfig {
    /** 配置文件。 */
    /** 配置文件路径（惰性取值，见 GamePaths 的说明）。 */
    private static Path file() {
        return GamePaths.config("sounds.json");
    }
    // disableHtmlEscaping：否则 Gson 会把说明文字里的 '=' 写成 &#61;，玩家读起来费劲
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    /** 总开关；关掉后不注入任何音频（音效全走原版、BGM 不播）。 */
    private static boolean enabled = true;
    /** 播放音量（0~1），音效与 BGM 共用。 */
    private static float volume = 1.0F;
    /**
     * 背景音乐使用的声道（原版音量分类）。
     *
     * <p>默认 {@code players}（原版"玩家"滑块）而不是 {@code music}，有两个原因：</p>
     * <ol>
     *   <li>本模组的 BGM 要能把原版背景音乐压到 {@link #vanillaMusicDuck}——
     *       两者若同在 {@code music} 声道，压低原版音乐就会连同自己的 BGM 一起压掉；</li>
     *   <li>不少玩家为了不听原版音乐把"音乐"滑块关成 0，那样 BGM 会一起静音。</li>
     * </ol>
     *
     * <p>可以改成 {@code music} / {@code ambient} / {@code master} / {@code record}；
     * 改回 {@code music} 时自动放弃压低原版音乐（见 {@link ClientBgm}）。</p>
     */
    private static String bgmCategory = "players";
    /** 播放本模组 BGM 时是否把原版背景音乐压低。 */
    private static boolean duckVanillaMusic = true;
    /** 原版背景音乐被压低到的音量（0~1）。 */
    private static float vanillaMusicDuck = 0.1F;

    /** 当前选用的音乐包 id（空 = 没选包，此时没有任何音频）。 */
    private static String activePack = "";

    /** 解析结果：音频键 → 候选文件（顺序 = 包内声明顺序）。 */
    private static final Map<String, List<CustomAudio.Candidate>> RESOLVED = new LinkedHashMap<>();
    /** 已扫描到的音乐包（懒加载）。 */
    private static List<SoundPack> packs = List.of();

    private CustomSoundConfig() {}

    /** 音乐包根目录。 */
    public static Path soundPackDir() {
        return SoundPack.root();
    }

    public static boolean enabled() {
        return enabled;
    }

    public static float volume() {
        return volume;
    }

    /** BGM 声道名（music / players / ambient / master / record）。 */
    public static String bgmCategory() {
        return bgmCategory;
    }

    /** 是否在播放本模组 BGM 时压低原版背景音乐。 */
    public static boolean duckVanillaMusic() {
        return duckVanillaMusic;
    }

    /** 原版背景音乐被压低到的音量（0~1）。 */
    public static float vanillaMusicDuck() {
        return vanillaMusicDuck;
    }

    /** 当前 BGM 声道的中文名（界面提示用）。 */
    public static String bgmCategoryLabel() {
        return bgmCategoryLabel(bgmCategory);
    }

    /** 指定声道名的中文名。 */
    public static String bgmCategoryLabel(String category) {
        return switch (category == null ? "" : category.toLowerCase(java.util.Locale.ROOT)) {
            case "players" -> "玩家";
            case "ambient" -> "环境";
            case "master" -> "主音量";
            case "record" -> "唱片机/音符盒";
            default -> "音乐";
        };
    }

    /** 把声道名解析成原版枚举；无法识别时退回 music。 */
    public static net.minecraft.sounds.SoundSource bgmSoundSource() {
        return switch (bgmCategory.toLowerCase(java.util.Locale.ROOT)) {
            case "players" -> net.minecraft.sounds.SoundSource.PLAYERS;
            case "ambient" -> net.minecraft.sounds.SoundSource.AMBIENT;
            case "master" -> net.minecraft.sounds.SoundSource.MASTER;
            case "record" -> net.minecraft.sounds.SoundSource.RECORDS;
            default -> net.minecraft.sounds.SoundSource.MUSIC;
        };
    }

    /** 该玩家把 BGM 所在声道设为 0 了吗（用于在配置界面提示"听不到"的原因）。 */
    public static boolean bgmCategoryMuted() {
        var mc = net.minecraft.client.Minecraft.getInstance();
        if (mc.options == null) return false;
        return mc.options.getSoundSourceVolume(bgmSoundSource()) <= 0.0F;
    }

    // === 音乐包 ===

    /** 已扫描到的音乐包。 */
    public static List<SoundPack> packs() {
        return packs;
    }

    /** 重新扫描音乐包目录（界面上「扫描」按钮用，免得玩家为了新放进去的包重开界面）。 */
    public static void reloadPacks() {
        packs = SoundPack.scan();
    }

    /** 当前选用的音乐包 id（空 = 没选）。 */
    public static String activePackId() {
        return activePack;
    }

    /** 当前音乐包；未选或找不到时返回 null。 */
    public static SoundPack activePack() {
        for (SoundPack p : packs) {
            if (p.id().equals(activePack)) return p;
        }
        return null;
    }

    // === 候选文件（解析结果）===

    /**
     * 某个音频键当前可播的候选文件（来自当前音乐包，顺序 = 包内声明顺序）。
     *
     * <p>返回空列表表示这个键没有音频——调用方要走各自的默认行为（音效退回原版、BGM 不播），
     * 否则会去播一个没有音频资源的 SoundEvent，游戏里表现为静默 + 日志警告。</p>
     */
    public static List<CustomAudio.Candidate> candidates(String key) {
        return RESOLVED.getOrDefault(key, List.of());
    }

    /** 该键是否有音频可播（且总开关开着）。 */
    public static boolean hasCandidates(String key) {
        return !candidates(key).isEmpty();
    }

    /** 该键当前候选文件数（BGM 播放器用它判断"要不要循环同一首"）。 */
    public static int candidateCount(String key) {
        return candidates(key).size();
    }

    /** 全部有音频的键（生成资源包用）。 */
    public static Map<String, List<CustomAudio.Candidate>> resolvedTable() {
        return Map.copyOf(RESOLVED);
    }

    /** 已解析出的候选总数（日志与界面提示用）。 */
    public static int resolvedCandidateTotal() {
        int n = 0;
        for (List<CustomAudio.Candidate> list : RESOLVED.values()) n += list.size();
        return n;
    }

    // === 加载 / 保存 / 解析 ===

    private static boolean loaded = false;

    /**
     * 确保配置已加载（只加载一次）。
     *
     * <p>两个调用点（客户端初始化、资源包发现）的先后顺序不该被依赖，
     * 故用这个幂等入口——否则"配置还没读就生成资源包"会静默地少打包音频。</p>
     */
    public static void ensureLoaded() {
        if (loaded) return;
        loaded = true;
        load();
    }

    /** 加载配置并解析出真正可用的候选；文件不存在时写出带说明的模板。 */
    public static void load() {
        Data data = new Data();
        try {
            if (Files.exists(file())) {
                data = GSON.fromJson(Files.readString(file()), Data.class);
            }
        } catch (Exception e) {
            CCReference.LOG.warn("读取音频配置失败，使用默认值: {}", e.toString());
        }
        if (data == null) data = new Data();

        enabled = data.enabled;
        volume = Math.max(0F, Math.min(1F, data.volume));
        bgmCategory = data.bgmCategory == null || data.bgmCategory.isBlank()
            ? "players" : data.bgmCategory.trim();
        duckVanillaMusic = data.duckVanillaMusic;
        vanillaMusicDuck = Math.max(0F, Math.min(1F, data.vanillaMusicDuck));
        activePack = data.activePack == null ? "" : data.activePack.trim();

        resolve();
        save();

        if (resolvedCandidateTotal() > 0) {
            CCReference.LOG.info("Crafty Cards 音频已就绪：{} 个键、{} 个候选文件（音乐包：{}）",
                RESOLVED.size(), resolvedCandidateTotal(),
                activePack.isEmpty() ? "未选用" : activePack);
        } else {
            CCReference.LOG.info("Crafty Cards 没有可播音频：请在音乐包里选用一个包（用 tools/soundpack_maker.py 制作）");
        }
    }

    /**
     * 逐键解析候选文件：只取**当前音乐包**里该键声明的条目，权重用包内声明的。
     *
     * <p>包里声明为权重 0 的条目在 {@link SoundPack} 解析时就已剔除，这里再按
     * {@code > 0} 过一遍是防御性的。</p>
     */
    private static void resolve() {
        RESOLVED.clear();
        if (!enabled) return;
        packs = SoundPack.scan();
        SoundPack pack = activePack();
        if (!activePack.isEmpty() && pack == null) {
            CCReference.LOG.warn("指定的音乐包不存在：{}（请在配置界面重新选一个）", activePack);
        }
        if (pack == null) return;
        for (String key : CustomAudio.allKeys()) {
            List<CustomAudio.Candidate> list = new ArrayList<>();
            for (SoundPack.Entry entry : pack.entriesFor(key)) {
                Path path = pack.pathFor(key, entry.file());
                if (path == null) continue;
                if (entry.weight() <= 0) continue;
                list.add(new CustomAudio.Candidate(packRef(pack.id(), entry.file()),
                    entry.file(), entry.weight(), path));
            }
            if (!list.isEmpty()) RESOLVED.put(key, List.copyOf(list));
        }
    }

    /** 包内候选的引用写法（标识候选来自哪个包的哪个文件，用于日志与去重）。 */
    public static String packRef(String packId, String file) {
        return "pack:" + packId + "/" + file;
    }

    /** 启用开关与音量的当前值（供界面初始化）。 */
    public static boolean enabledValue() {
        return enabled;
    }

    public static float volumeValue() {
        return volume;
    }

    /**
     * 应用界面上的改动：写入配置、重新解析（含文件存在性校验）。
     *
     * <p>调用方随后应重新生成资源包并触发资源重载，改动才会真正生效——
     * 音频必须经资源包被游戏加载，这一步绕不开。</p>
     *
     * <p>只接收界面上还存在的三项（总开关 / 音量 / 选哪个包）；
     * BGM 声道与压低原版音乐不再有界面入口，保留配置文件里的原值不动（{@code load()} 已读入）。</p>
     */
    public static void applySelection(boolean enabledValue, float volumeValue, String packId) {
        enabled = enabledValue;
        volume = Math.max(0F, Math.min(1F, volumeValue));
        activePack = packId == null ? "" : packId.trim();

        resolve();
        save();
    }

    private static void save() {
        try {
            Files.createDirectories(file().getParent());
            Data out = new Data();
            out.enabled = enabled;
            out.volume = volume;
            out.bgmCategory = bgmCategory;
            out.duckVanillaMusic = duckVanillaMusic;
            out.vanillaMusicDuck = vanillaMusicDuck;
            out.activePack = activePack;
            Files.writeString(file(), GSON.toJson(out));
        } catch (IOException e) {
            CCReference.LOG.warn("保存音频配置失败: {}", e.toString());
        }
    }

    /** Gson 载体。{@code _help} 只写不读，作用是让配置文件自带说明。 */
    private static class Data {
        @SuppressWarnings("unused")
        public List<String> _help = List.of(
            "音频只有一个来源：音乐包。模组 jar 里不带任何 .ogg，也不再有\"自备单文件\"那条路。",
            "",
            "音乐包：config/crafty_cards/soundpacks/<包id>/pack.json（含 .ogg），",
            "  用 tools/soundpack_maker.py 制作与修改，游戏内只读取与选用、从不写回。",
            "  一个音频键可以在包里配多个文件、各带权重（{file, weight}），",
            "  播放时由游戏按权重随机挑一个（所以\"不要\"这类音效可以准备多条）。",
            "",
            "activePack：选用哪个音乐包（留空 = 无音频：音效走原版、BGM 不播）。",
            "  改包、增删文件后在游戏内「设置主页 → 音乐包」点「扫描」再「保存并应用」。",
            "",
            "bgmCategory：BGM 用哪个音量声道。默认 players（原版\"玩家\"滑块）——",
            "  一来要把原版音乐压低时，同声道的 BGM 会被一起压掉；",
            "  二来很多人为关掉原版音乐把\"音乐\"滑块调成 0，那样 BGM 会一起静音。",
            "duckVanillaMusic / vanillaMusicDuck：播 BGM 时是否压低原版背景音乐、压到多低（默认 0.1）。",
            "  这两项界面上没有入口，改这里即可（改完重启生效）。",
            "",
            "enabled / volume：模组音频的总开关与音量，在游戏内「设置主页 → 音乐包」里调。",
            "（历史遗留的 localFiles / weights 两个键已废弃，写了也不会生效，可以删掉。）");

        public boolean enabled = true;
        public float volume = 1.0F;
        public String bgmCategory = "players";
        public boolean duckVanillaMusic = true;
        public float vanillaMusicDuck = 0.1F;
        public String activePack = "";
    }
}
