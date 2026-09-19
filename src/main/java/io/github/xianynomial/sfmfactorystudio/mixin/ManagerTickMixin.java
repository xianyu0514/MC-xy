package io.github.xianynomial.sfmfactorystudio.mixin;

import ca.teamdman.sfm.common.blockentity.ManagerBlockEntity;
import ca.teamdman.sfml.ast.Program;
import io.github.xianynomial.sfmfactorystudio.chronosfm.ChronoSfmRuntime;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * SFM manager hot-path wrapper.
 *
 * ChronoSFM may remove provably redundant computation, but it MUST NOT defer or
 * drop a due SFM execution. Throughput-preserving research therefore excludes
 * the legacy Factory Studio TpsBackoff/tick-budget path entirely.
 */
@Mixin(value = ManagerBlockEntity.class)
public abstract class ManagerTickMixin {
    @Redirect(
            method = "serverTick",
            at = @At(
                    value = "INVOKE",
                    target = "Lca/teamdman/sfml/ast/Program;tick(Lca/teamdman/sfm/common/blockentity/ManagerBlockEntity;)Z"
            ),
            require = 0
    )
    private static boolean sfmfactorystudio$chronoTick(Program program, ManagerBlockEntity manager) {
        // #602-style fast gate: no ProgramContext/network/label setup when every
        // trigger is mathematically proven inactive. This does NOT skip a due
        // trigger and therefore cannot reduce legacy transfer cadence.
        if (!ChronoSfmRuntime.shouldExecute(program, manager)) {
            manager.clearRedstonePulseQueue();
            return false;
        }

        // Hard throughput invariant: every due legacy execution still runs.
        // No tick-budget admission control, idle backoff or starvation queue is
        // allowed on the ChronoSFM path.
        return program.tick(manager);
    }
}
