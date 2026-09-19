package io.github.xianynomial.sfmfactorystudio.mixin;

import ca.teamdman.sfm.common.label.LabelPositionHolder;
import ca.teamdman.sfm.common.util.BlockPosSet;
import io.github.xianynomial.sfmfactorystudio.chronosfm.ChronoRevisionSource;
import net.minecraft.core.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.function.BiPredicate;
import java.util.function.Predicate;

/**
 * Conservative structural revision for SFM label -> position mappings.
 *
 * getPositionsMut() bumps immediately because it exposes a mutable BlockPosSet;
 * this may invalidate more often than strictly necessary, but it must never
 * miss a mutation performed through that mutable view.
 */
@Mixin(value = LabelPositionHolder.class)
public abstract class LabelPositionHolderRevisionMixin implements ChronoRevisionSource {
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
            method = "getPositionsMut(Ljava/lang/String;)Lca/teamdman/sfm/common/util/BlockPosSet;",
            at = @At("HEAD"),
            require = 0
    )
    private void chronosfm$mutablePositions(
            String label,
            CallbackInfoReturnable<BlockPosSet> cir
    ) {
        chronosfm$bump();
    }

    @Inject(
            method = "removeAll(Lnet/minecraft/core/BlockPos;)Lca/teamdman/sfm/common/label/LabelPositionHolder;",
            at = @At("HEAD"),
            require = 0
    )
    private void chronosfm$removeAllPos(
            BlockPos pos,
            CallbackInfoReturnable<LabelPositionHolder> cir
    ) {
        chronosfm$bump();
    }

    @Inject(
            method = "removeAll(J)Lca/teamdman/sfm/common/label/LabelPositionHolder;",
            at = @At("HEAD"),
            require = 0
    )
    private void chronosfm$removeAllLong(
            long pos,
            CallbackInfoReturnable<LabelPositionHolder> cir
    ) {
        chronosfm$bump();
    }

    @Inject(
            method = "prune()Lca/teamdman/sfm/common/label/LabelPositionHolder;",
            at = @At("HEAD"),
            require = 0
    )
    private void chronosfm$prune(CallbackInfoReturnable<LabelPositionHolder> cir) {
        chronosfm$bump();
    }

    @Inject(
            method = "clear()Lca/teamdman/sfm/common/label/LabelPositionHolder;",
            at = @At("HEAD"),
            require = 0
    )
    private void chronosfm$clear(CallbackInfoReturnable<LabelPositionHolder> cir) {
        chronosfm$bump();
    }

    @Inject(
            method = "removeIf(Ljava/util/function/BiPredicate;)Lca/teamdman/sfm/common/label/LabelPositionHolder;",
            at = @At("HEAD"),
            require = 0
    )
    private void chronosfm$removeIfBi(
            BiPredicate<String, BlockPos> predicate,
            CallbackInfoReturnable<LabelPositionHolder> cir
    ) {
        chronosfm$bump();
    }

    @Inject(
            method = "removeIf(Ljava/util/function/Predicate;)Lca/teamdman/sfm/common/label/LabelPositionHolder;",
            at = @At("HEAD"),
            require = 0
    )
    private void chronosfm$removeIf(
            Predicate<String> predicate,
            CallbackInfoReturnable<LabelPositionHolder> cir
    ) {
        chronosfm$bump();
    }
}
