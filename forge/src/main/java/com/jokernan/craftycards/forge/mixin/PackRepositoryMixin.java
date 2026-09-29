package com.jokernan.craftycards.forge.mixin;

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

/** 客户端音频资源包来源：只注入含 ClientPackSource 的仓库，每次重载重新生成音乐包。 */
@Mixin(PackRepository.class)
public abstract class PackRepositoryMixin {
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



