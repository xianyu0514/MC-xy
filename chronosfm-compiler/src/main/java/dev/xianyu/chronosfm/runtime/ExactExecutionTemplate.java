package dev.xianyu.chronosfm.runtime;

import dev.xianyu.chronosfm.ir.CompiledProgramPlan;
import dev.xianyu.chronosfm.ir.CompiledTrigger;
import dev.xianyu.chronosfm.ir.ExactOperation;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * Immutable exact-order execution template.
 *
 * Trigger -> operation grouping and ordering are performed once at compile/bind
 * time. The tick hot path only evaluates trigger timing and walks prebuilt
 * arrays. No per-tick filtering, sorting or temporary operation lists are
 * required.
 *
 * This class does not mutate Minecraft. A caller-supplied executor performs the
 * final operation, which allows the SFM bridge to keep original capability
 * semantics.
 */
public final class ExactExecutionTemplate {
    public static final int LEGACY_REQUIRED = -1;

    private final CompiledTrigger[] triggers;
    private final ExactOperation[][] operationsByTrigger;
    private final boolean requiresLegacy;

    public ExactExecutionTemplate(CompiledProgramPlan plan) {
        Objects.requireNonNull(plan, "plan");
        this.triggers = plan.triggers().toArray(CompiledTrigger[]::new);
        this.operationsByTrigger = buildOperationsByTrigger(plan, triggers.length);
        this.requiresLegacy = plan.requiresLegacyExecution() || hasLegacyTrigger(triggers);
    }

    /**
     * Executes due compiled operations in original trigger + statement order.
     *
     * @return executed operation count, or LEGACY_REQUIRED before executing
     *         anything when the plan contains unsupported semantics.
     */
    public int executeDue(
            long localTick,
            long globalTick,
            int redstonePulses,
            OperationExecutor executor
    ) {
        Objects.requireNonNull(executor, "executor");
        if (requiresLegacy) return LEGACY_REQUIRED;

        int executed = 0;
        for (int triggerIndex = 0; triggerIndex < triggers.length; triggerIndex++) {
            CompiledTrigger trigger = triggers[triggerIndex];
            int repetitions = repetitions(trigger, localTick, globalTick, redstonePulses);
            if (repetitions == 0) continue;

            ExactOperation[] operations = operationsByTrigger[triggerIndex];
            for (int repetition = 0; repetition < repetitions; repetition++) {
                for (int operationIndex = 0; operationIndex < operations.length; operationIndex++) {
                    executor.execute(operations[operationIndex]);
                    executed++;
                }
            }
        }
        return executed;
    }

    public boolean requiresLegacy() {
        return requiresLegacy;
    }

    public int operationCountForTrigger(int triggerIndex) {
        return operationsByTrigger[triggerIndex].length;
    }

    private static int repetitions(
            CompiledTrigger trigger,
            long localTick,
            long globalTick,
            int redstonePulses
    ) {
        return switch (trigger.mode()) {
            case LEGACY -> 0;
            case FAST_REDSTONE -> Math.max(0, redstonePulses);
            case FAST_TIMER -> trigger.isDue(localTick, globalTick, redstonePulses) ? 1 : 0;
        };
    }

    private static boolean hasLegacyTrigger(CompiledTrigger[] triggers) {
        for (CompiledTrigger trigger : triggers) {
            if (trigger.mode() == CompiledTrigger.Mode.LEGACY) return true;
        }
        return false;
    }

    @SuppressWarnings("unchecked")
    private static ExactOperation[][] buildOperationsByTrigger(
            CompiledProgramPlan plan,
            int triggerCount
    ) {
        List<ExactOperation>[] grouped = new List[triggerCount];
        for (int i = 0; i < triggerCount; i++) grouped[i] = new ArrayList<>();

        for (ExactOperation operation : plan.exactOperations()) {
            grouped[operation.triggerIndex()].add(operation);
        }

        ExactOperation[][] result = new ExactOperation[triggerCount][];
        for (int i = 0; i < triggerCount; i++) {
            grouped[i].sort(Comparator.comparingInt(ExactOperation::exactOrderOrdinal));
            result[i] = grouped[i].toArray(ExactOperation[]::new);
        }
        return result;
    }

    @FunctionalInterface
    public interface OperationExecutor {
        void execute(ExactOperation operation);
    }
}
