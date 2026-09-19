package io.github.xianynomial.sfmfactorystudio.mixin;

import ca.teamdman.sfm.common.program.ExecuteProgramBehaviour;
import ca.teamdman.sfm.common.program.LimitedInputSlotObjectPool;
import ca.teamdman.sfm.common.program.LimitedOutputSlotObjectPool;
import ca.teamdman.sfm.common.program.ProgramContext;
import ca.teamdman.sfml.ast.Program;
import ca.teamdman.sfml.ast.Trigger;
import org.apache.logging.log4j.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

/**
 * Production-only Program.tick(context) fast path.
 *
 * When execution uses the stateless ExecuteProgramBehaviour and logging is OFF,
 * upstream trigger timing/logging is pure diagnostics. This path preserves:
 *
 * - exact trigger list order
 * - Trigger.shouldTick(context)
 * - context.didSomething semantics
 * - ProgramContext fork/free lifecycle
 * - Trigger.tick(), including redstone pulse repetition
 * - input/output object-pool invariants
 *
 * Simulation/linting/custom behaviours or enabled logs use upstream SFM.
 */
@Mixin(value = Program.class)
public abstract class ProgramTickFastPathMixin {
    @Shadow
    public abstract List<Trigger> triggers();

    @Inject(
            method = "tick(Lca/teamdman/sfm/common/program/ProgramContext;)V",
            at = @At("HEAD"),
            cancellable = true,
            require = 0
    )
    private void chronosfm$skipDisabledTriggerDiagnostics(
            ProgramContext context,
            CallbackInfo ci
    ) {
        if (context == null || context.getLogger() == null) return;
        if (context.getLogger().getLogLevel() != Level.OFF) return;
        if (!(context.getBehaviour() instanceof ExecuteProgramBehaviour)) return;

        LimitedInputSlotObjectPool.checkInvariant();
        LimitedOutputSlotObjectPool.checkInvariant();

        List<Trigger> triggers = triggers();
        for (int i = 0, size = triggers.size(); i < size; i++) {
            Trigger trigger = triggers.get(i);
            if (!trigger.shouldTick(context)) continue;

            if (!context.didSomething()) {
                context.setDidSomething(true);
            }

            ProgramContext forkedContext = context.fork();
            trigger.tick(forkedContext);
            forkedContext.free();
        }

        LimitedInputSlotObjectPool.checkInvariant();
        LimitedOutputSlotObjectPool.checkInvariant();
        ci.cancel();
    }
}
