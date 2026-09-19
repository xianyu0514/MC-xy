package io.github.xianynomial.sfmfactorystudio.mixin;

import ca.teamdman.sfm.common.block_network.BlockNetwork;
import io.github.xianynomial.sfmfactorystudio.chronosfm.ChronoRevisionSource;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Structural topology revision for SFM block networks.
 *
 * Identity + revision is used by ChronoSFM cache stamps. New split/merged
 * network objects naturally get a new identity; in-place membership mutations
 * increment this revision.
 */
@Mixin(value = BlockNetwork.class)
public abstract class BlockNetworkRevisionMixin implements ChronoRevisionSource {
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

    @Inject(
            method = "addMember(Lnet/minecraft/core/BlockPos;Ljava/lang/Object;)V",
            at = @At("HEAD"),
            require = 0
    )
    private void chronosfm$addMember(BlockPos pos, Object member, CallbackInfo ci) {
        chronosfm$bump();
    }

    @Inject(
            method = "removeMember(Lnet/minecraft/core/BlockPos;)V",
            at = @At("HEAD"),
            require = 0
    )
    private void chronosfm$removeMember(BlockPos pos, CallbackInfo ci) {
        chronosfm$bump();
    }

    @Inject(
            method = "purgeChunk(Lnet/minecraft/world/level/ChunkPos;)V",
            at = @At("HEAD"),
            require = 0
    )
    private void chronosfm$purgeChunk(ChunkPos chunkPos, CallbackInfo ci) {
        chronosfm$bump();
    }

    @Inject(
            method = "addAllFromOtherNetwork(Lca/teamdman/sfm/common/block_network/BlockNetwork;)V",
            at = @At("HEAD"),
            require = 0
    )
    private void chronosfm$merge(BlockNetwork<?, ?> other, CallbackInfo ci) {
        chronosfm$bump();
    }
}
