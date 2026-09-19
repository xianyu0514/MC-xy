package io.github.xianynomial.sfmfactorystudio.mixin;

import ca.teamdman.sfm.common.block_network.SFMBlockCapabilityCacheForLevel;
import ca.teamdman.sfm.common.capability.SFMBlockCapabilityKind;
import ca.teamdman.sfm.common.capability.SFMBlockCapabilityResult;
import io.github.xianynomial.sfmfactorystudio.chronosfm.ChronoRevisionSource;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.ChunkPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Capability-structure revision.
 *
 * SFM remains the owner of capability discovery and invalidation. ChronoSFM
 * observes only a monotonic version, never a second capability cache.
 */
@Mixin(value = SFMBlockCapabilityCacheForLevel.class)
public abstract class CapabilityCacheRevisionMixin implements ChronoRevisionSource {
    @Unique
    private long chronosfm$revision;

    @Override
    public long chronosfm$getRevision() {
        return chronosfm$revision;
    }

    @Unique
    private void chronosfm$bump() {
        chronosfm$revision++;
    }

    @Inject(method = "clear()V", at = @At("HEAD"), require = 0)
    private void chronosfm$clear(CallbackInfo ci) {
        chronosfm$bump();
    }

    @Inject(
            method = "remove(Lnet/minecraft/core/BlockPos;Lca/teamdman/sfm/common/capability/SFMBlockCapabilityKind;Lnet/minecraft/core/Direction;)V",
            at = @At("HEAD"),
            require = 0
    )
    private void chronosfm$remove(
            BlockPos pos,
            SFMBlockCapabilityKind<?> kind,
            Direction direction,
            CallbackInfo ci
    ) {
        chronosfm$bump();
    }

    @Inject(
            method = "putCapability(Lnet/minecraft/core/BlockPos;Lca/teamdman/sfm/common/capability/SFMBlockCapabilityKind;Lnet/minecraft/core/Direction;Lca/teamdman/sfm/common/capability/SFMBlockCapabilityResult;)V",
            at = @At("HEAD"),
            require = 0
    )
    private void chronosfm$put(
            BlockPos pos,
            SFMBlockCapabilityKind<?> kind,
            Direction direction,
            SFMBlockCapabilityResult<?> result,
            CallbackInfo ci
    ) {
        chronosfm$bump();
    }

    @Inject(
            method = "overwriteFromOther(Lnet/minecraft/core/BlockPos;Lca/teamdman/sfm/common/block_network/SFMBlockCapabilityCacheForLevel;)V",
            at = @At("HEAD"),
            require = 0
    )
    private void chronosfm$overwrite(
            BlockPos pos,
            SFMBlockCapabilityCacheForLevel other,
            CallbackInfo ci
    ) {
        chronosfm$bump();
    }

    @Inject(
            method = "bustCacheForChunk(Lnet/minecraft/world/level/ChunkPos;)V",
            at = @At("HEAD"),
            require = 0
    )
    private void chronosfm$bustChunk(ChunkPos chunkPos, CallbackInfo ci) {
        chronosfm$bump();
    }
}
