package com.jokernan.craftycards.client;

import com.jokernan.craftycards.CCReference;
import com.jokernan.craftycards.game.CustomAudio;
import com.jokernan.craftycards.platform.GamePaths;
import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.PackSelectionConfig;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.PathPackResources;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackSource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 把音频目录**生成为一个资源包并注入**游戏。
 *
 * <p>为什么必须这样做：Minecraft 只能播放经资源包加载的音效，无法在运行时直接读磁盘上的
 * 任意音频文件。所以流程是——把当前音乐包里生效的候选文件复制进一个
 * 生成的资源包目录，连同生成的 {@code sounds.json}（一个键下按权重列出多个文件，
 * 原版引擎据此随机挑一个），再以**最高优先级**注册给游戏。</p>
 *
 * <p><b>这个包是音频的唯一来源</b>：模组 jar 里既不放 {@code sounds.json} 也不放任何 {@code .ogg}，
 * 玩家装的音乐包都经这里变成游戏能播的资源。生成的目录每次启动、
 * 每次保存配置后都会重写，所以换包、改权重、加文件都会自动跟上。</p>
 *
 * <p>本类**只在 common**：生成是纯文件 IO，Pack 构造也全是原版 API；把来源交给游戏的机制
 * 各加载器自己负责（NeoForge 走打包来源注册事件、Fabric 走 {@code PackRepository} 的 mixin）。</p>
 */
public final class CustomSoundPack {
    /** 1.21.1 的资源包格式（取自游戏自带的版本清单）。 */
    private static final int RESOURCE_PACK_FORMAT = 34;
    /** 生成资源包的名字与描述（显示在资源包列表里）。 */
    private static final String PACK_DESCRIPTION = "Crafty Cards · 音乐包音频";

    private CustomSoundPack() {}

    /** 生成的资源包目录（由模组维护，玩家不需要动）。 */
    private static Path packDir() {
        return GamePaths.config("sounds_pack");
    }

    /**
     * 生成（或重建）资源包目录内容。
     *
     * <p>没有音频时也照样写（空的 {@code sounds.json}）：资源包是在启动时注册进资源包仓库的，
     * 若"启动时没有音频"就不注册，之后在游戏内选了音乐包再触发资源重载也捡不到它
     * （重载只会重跑已注册的源）。空包不覆盖任何东西，代价可以忽略。</p>
     */
    public static void regenerate() {
        generate();
    }

    /** 上一次生成复制成功的候选文件数（界面提示用）。 */
    private static int lastCopied = 0;

    /** 上一次生成复制成功的候选文件数。 */
    public static int lastCopiedCount() {
        return lastCopied;
    }

    /**
     * 生成资源包内容：把每个键的候选文件复制进去，并写出带权重的 {@code sounds.json}。
     *
     * <p>只写"确实复制成功"的候选——否则会生成指向空气的条目，游戏里表现为静默 + 日志告警。
     * 源文件可能来自玩家目录、也可能来自当前音乐包，{@link CustomSoundConfig#resolvedTable()}
     * 已经把两者统一成了候选列表。</p>
     *
     * @return 复制成功的候选总数
     */
    private static int generate() {
        Path dir = packDir();
        Path assets = dir.resolve("assets/crafty_cards");
        Path customDir = assets.resolve("sounds/custom");
        try {
            // 先清掉上一次生成的文件：换包、删文件之后，残留的旧 ogg 不该继续占地方
            deleteRecursively(customDir);
            Files.createDirectories(customDir);
            Files.writeString(dir.resolve("pack.mcmeta"), CustomAudio.packMcMeta(RESOURCE_PACK_FORMAT));

            Map<String, List<CustomAudio.Candidate>> table = new LinkedHashMap<>();
            int copied = 0;
            for (Map.Entry<String, List<CustomAudio.Candidate>> entry
                : CustomSoundConfig.resolvedTable().entrySet()) {
                String key = entry.getKey();
                List<CustomAudio.Candidate> kept = new ArrayList<>();
                int index = 0;
                for (CustomAudio.Candidate candidate : entry.getValue()) {
                    Path dst = assets.resolve("sounds/" + CustomAudio.audioFileName(key, index) + ".ogg");
                    try {
                        Files.copy(candidate.path(), dst, StandardCopyOption.REPLACE_EXISTING);
                    } catch (IOException e) {
                        CCReference.LOG.warn("复制音频失败，已跳过 {}: {}", candidate.path(), e.toString());
                        continue;
                    }
                    kept.add(candidate);
                    index++;
                    copied++;
                }
                if (!kept.isEmpty()) table.put(key, List.copyOf(kept));
            }
            Files.writeString(assets.resolve("sounds.json"), CustomAudio.soundsJson(table));
            lastCopied = copied;
            return copied;
        } catch (IOException e) {
            // 音频属于锦上添花，出错不能让游戏起不来——退回"没有音频"
            CCReference.LOG.warn("生成音频资源包失败，将使用原版音效: {}", e.toString());
            lastCopied = 0;
            return 0;
        }
    }

    /** 删除目录内容（每次重建时清掉上一次的残留文件）。目录不存在时什么都不做。 */
    private static void deleteRecursively(Path dir) throws IOException {
        if (!Files.isDirectory(dir)) return;
        try (var stream = Files.walk(dir)) {
            for (Path p : stream.sorted(java.util.Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(p);
            }
        }
    }

    /**
     * 构造"生成目录"对应的资源包（始终启用、最高优先级），交给各加载器注入。
     *
     * <p>构造用的全是原版 API；**差别只在怎么把它交给游戏**：NeoForge 有
     * {@code AddPackFindersEvent#addRepositorySource}，Fabric 没有对应事件，
     * 只能在 {@code PackRepository} 构造时往它的来源集合里加一个（见
     * {@code fabric/.../mixin/PackRepositoryMixin}）。</p>
     *
     * <p>始终启用（{@code required = true}）是为了让它稳定覆盖模组默认行为；
     * 玩家要关掉应该去改配置里的 {@code enabled}，而不是在资源包界面里关——那样只会
     * 造成"配置里写了文件名却没声音"的困惑。位置取 TOP，保证优先于其它资源包。</p>
     *
     * <p>每次都重新生成：内容可能是空的，但源必须在，否则游戏内保存后重载捡不到新文件
     * （{@code reloadResourcePacks} 只重跑已注册的源，不会再触发注入）。</p>
     *
     * @return 资源包；配置里关掉音频时返回 {@code null}（此时两个加载器都不注入）
     */
    public static Pack buildPack() {
        CustomSoundConfig.ensureLoaded();
        if (!CustomSoundConfig.enabled()) return null;
        generate();

        Path dir = packDir();
        PackLocationInfo info = new PackLocationInfo(
            "crafty_cards/custom_sounds",
            Component.literal(PACK_DESCRIPTION),
            PackSource.BUILT_IN,
            Optional.empty());
        // 用原版公开的 ResourcesSupplier：NeoForge 侧的 BuiltInPackSource.fromName 是它自己补的 API，
        // 原版没有（原版 BuiltInPackSource 是抽象类），换了以后两边同一份代码
        Pack.ResourcesSupplier supplier = new PathPackResources.PathResourcesSupplier(dir);
        Pack pack = Pack.readMetaAndCreate(info, supplier, PackType.CLIENT_RESOURCES,
            new PackSelectionConfig(true, Pack.Position.TOP, false));
        CCReference.LOG.info("Crafty Cards 音频资源包已就绪（{} 个键、{} 个候选文件）",
            CustomSoundConfig.resolvedTable().size(), CustomSoundConfig.resolvedCandidateTotal());
        return pack;
    }
}
