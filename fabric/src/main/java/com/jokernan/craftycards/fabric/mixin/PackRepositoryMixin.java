package com.jokernan.craftycards.fabric.mixin;

import com.jokernan.craftycards.client.CustomSoundPack;
import net.minecraft.client.resources.ClientPackSource;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackRepository;
import net.minecraft.server.packs.repository.RepositorySource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * 音频资源包注入（Fabric 侧）：往资源包仓库的来源集合里加一个"我们的音频包"来源。
 *
 * <p><b>为什么必须 mixin</b>：NeoForge 有打包来源注册事件（可以在游戏组装资源包仓库时插一脚），
 * 原版/Fabric 没有——{@code PackRepository} 只在构造时收一批来源，之后**没有**公开的
 * "再加一个来源"的方法（`javap -p` 看过：内部是 {@code private final Set<RepositorySource> sources}）。
 * 所以这里在构造末尾把我们的来源加进去。</p>
 *
 * <p><b>为什么来源是个 lambda 而不是一个现成的东西</b>：来源是"懒"的——每次
 * {@code reload()}（启动、切资源包、游戏内保存音频后重载）都会重新问一遍。
 * 因此把"重新生成目录内容 + 构造 Pack"放进 lambda 里，正好让每次重载都拿到**最新**的音频，
 * 不需要别处再记得调一次生成。</p>
 *
 * <p>专用服务器上直接跳过：音频是纯客户端的事，那边连 {@code sounds.json} 都不读。</p>
 */
@Mixin(PackRepository.class)
public abstract class PackRepositoryMixin {
    /**
     * 资源包来源集合（原版私有字段，构造时收进来的那批）。
     *
     * <p>原版保存不可变集合，必须复制后整体替换。{@code @Final} 描述目标字段，
     * {@code @Mutable} 允许 mixin 写回；注解 {@code @Final} 不等于 Java 的 final 修饰符。</p>
     */
    @Final
    @Mutable
    @Shadow
    private Set<RepositorySource> sources;

    @Inject(method = "<init>", at = @At("TAIL"))
    private void crafty_cards$addSoundPackSource(CallbackInfo ci) {
        // Client-only mixin, but integrated servers also construct data-pack repositories.
        // Only the repository with vanilla client assets may receive our audio pack.
        if (this.sources.stream().noneMatch(ClientPackSource.class::isInstance)) return;

        Set<RepositorySource> merged = new LinkedHashSet<>(this.sources);
        merged.add(consumer -> {
            Pack pack = CustomSoundPack.buildPack();
            if (pack != null) {
                consumer.accept(pack);
            }
        });
        this.sources = merged;
    }
}
