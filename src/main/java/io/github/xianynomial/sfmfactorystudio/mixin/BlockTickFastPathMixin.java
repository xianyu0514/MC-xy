package io.github.xianynomial.sfmfactorystudio.mixin;

import ca.teamdman.sfm.common.program.ProgramContext;
import ca.teamdman.sfml.ast.Block;
import ca.teamdman.sfml.ast.Statement;
import org.apache.logging.log4j.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

/**
 * Removes per-statement diagnostic timing when SFM logging is OFF.
 *
 * Upstream Block.tick wraps every statement in System.nanoTime() and prepares an
 * INFO timing log. For production managers with logging disabled this work has
 * no observable logistics effect but is paid for every due statement.
 *
 * The fast path preserves the exact statement list and exact source order. Any
 * enabled logger uses the untouched upstream implementation.
 */
@Mixin(value = Block.class)
public abstract class BlockTickFastPathMixin {
    @Shadow
    public abstract List<Statement> statements();

    @Inject(method = "tick", at = @At("HEAD"), cancellable = true, require = 0)
    private void chronosfm$skipDisabledDiagnosticTiming(
            ProgramContext context,
            CallbackInfo ci
    ) {
        if (context == null || context.getLogger() == null) return;
        if (context.getLogger().getLogLevel() != Level.OFF) return;

        List<Statement> statements = statements();
        for (int i = 0, size = statements.size(); i < size; i++) {
            statements.get(i).tick(context);
        }
        ci.cancel();
    }
}
