package io.github.xianynomial.sfmfactorystudio.mixin;

import ca.teamdman.sfm.common.blockentity.ManagerBlockEntity;
import ca.teamdman.sfml.ast.Program;
import io.github.xianynomial.sfmfactorystudio.chronosfm.ChronoSfmRuntime;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * SFM manager hot-path wrapper for the throughput-preserving ChronoSFM runtime.
 *
 * Hard invariant: this path may skip a Program.tick only when all triggers are
 * mathematically proven inactive in the original SFM semantics. It never delays
 * a due trigger, applies a tick budget, or changes timer frequency.
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
        // Safe #602-style fast gate: no context/network/label setup if every
        // trigger is mathematically known to be inactive this tick.
        if (!ChronoSfmRuntime.shouldExecute(program, manager)) {
            manager.clearRedstonePulseQueue();
            return false;
        }

        // No TPS budget/backoff is allowed here. If SFM was due to execute this
        // tick, the original SFM Program.tick runs exactly once.
        return program.tick(manager);
    }
}
