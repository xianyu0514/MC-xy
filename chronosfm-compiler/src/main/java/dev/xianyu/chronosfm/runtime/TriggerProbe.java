package dev.xianyu.chronosfm.runtime;

import dev.xianyu.chronosfm.ir.CompiledProgramPlan;
import dev.xianyu.chronosfm.ir.CompiledTrigger;

import java.util.Arrays;
import java.util.Objects;

/**
 * Zero-allocation production preflight used before an expensive SFM
 * ProgramContext is built.
 *
 * Diagnostic probe() still exposes exact due-trigger indexes for tests and
 * tooling. The server hot path must use requiresFullContext()/maySkipFullContext().
 */
public final class TriggerProbe {
    private final CompiledTrigger[] triggers;
    private final boolean alwaysRequiresContext;

    public TriggerProbe(CompiledProgramPlan plan) {
        Objects.requireNonNull(plan, "plan");
        this.triggers = plan.triggers().toArray(CompiledTrigger[]::new);

        boolean alwaysDue = false;
        for (CompiledTrigger trigger : triggers) {
            if (trigger.mode() == CompiledTrigger.Mode.LEGACY
                    || (trigger.mode() == CompiledTrigger.Mode.FAST_TIMER
                    && trigger.intervalTicks() == 1)) {
                alwaysDue = true;
                break;
            }
        }
        this.alwaysRequiresContext = alwaysDue;
    }

    /**
     * Production hot path. Performs no heap allocation.
     */
    public boolean requiresFullContext(long localTick, long globalTick, int redstonePulses) {
        if (alwaysRequiresContext) return true;

        for (int i = 0; i < triggers.length; i++) {
            if (triggers[i].isDue(localTick, globalTick, redstonePulses)) {
                return true;
            }
        }
        return false;
    }

    public boolean maySkipFullContext(long localTick, long globalTick, int redstonePulses) {
        return !requiresFullContext(localTick, globalTick, redstonePulses);
    }

    public Result probe(long localTick, long globalTick, int redstonePulses) {
        int[] scratch = new int[triggers.length];
        int count = 0;
        boolean legacyDue = false;

        for (CompiledTrigger trigger : triggers) {
            if (!trigger.isDue(localTick, globalTick, redstonePulses)) continue;
            scratch[count++] = trigger.triggerIndex();
            legacyDue |= trigger.mode() == CompiledTrigger.Mode.LEGACY;
        }

        return new Result(Arrays.copyOf(scratch, count), legacyDue);
    }

    public Result probe(long localTick, long globalTick) {
        return probe(localTick, globalTick, 0);
    }

    public boolean alwaysRequiresContext() {
        return alwaysRequiresContext;
    }

    public record Result(int[] dueTriggerIndexes, boolean requiresLegacyContext) {
        public Result {
            dueTriggerIndexes = dueTriggerIndexes.clone();
        }

        public boolean maySkipFullContext() {
            return dueTriggerIndexes.length == 0;
        }

        @Override
        public int[] dueTriggerIndexes() {
            return dueTriggerIndexes.clone();
        }
    }
}
