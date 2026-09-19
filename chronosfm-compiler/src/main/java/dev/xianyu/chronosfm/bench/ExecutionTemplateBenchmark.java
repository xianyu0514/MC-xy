package dev.xianyu.chronosfm.bench;

import dev.xianyu.chronosfm.compiler.ChronoSfmCompiler;
import dev.xianyu.chronosfm.ir.ExactOperation;
import dev.xianyu.chronosfm.model.ProgramModel;
import dev.xianyu.chronosfm.model.StatementModel;
import dev.xianyu.chronosfm.model.TriggerModel;
import dev.xianyu.chronosfm.runtime.ExactExecutionTemplate;

import java.util.ArrayList;
import java.util.List;

public final class ExecutionTemplateBenchmark {
    private ExecutionTemplateBenchmark() {
    }

    public static void main(String[] args) {
        int triggers = args.length > 0 ? Integer.parseInt(args[0]) : 100;
        int operationsPerTrigger = args.length > 1 ? Integer.parseInt(args[1]) : 100;
        int ticks = args.length > 2 ? Integer.parseInt(args[2]) : 100_000;

        List<TriggerModel> triggerModels = new ArrayList<>(triggers);
        for (int trigger = 0; trigger < triggers; trigger++) {
            List<StatementModel> operations = new ArrayList<>(operationsPerTrigger);
            for (int op = 0; op < operationsPerTrigger; op++) {
                operations.add(new StatementModel.Input(
                        new StatementModel.EndpointSelector(
                                List.of("label-" + op), List.of(), "ALL", "UNMODIFIED"
                        ),
                        new StatementModel.ResourceSelector(List.of(), ""),
                        false
                ));
            }
            triggerModels.add(new TriggerModel.Timer(
                    20,
                    TriggerModel.Alignment.LOCAL,
                    trigger % 20,
                    operations
            ));
        }

        var plan = new ChronoSfmCompiler().compile(new ProgramModel(triggerModels));
        var template = new ExactExecutionTemplate(plan);
        CountingExecutor executor = new CountingExecutor();

        for (int tick = 0; tick < 5_000; tick++) {
            template.executeDue(tick, tick, 0, executor);
        }
        executor.count = 0;

        long start = System.nanoTime();
        int executed = 0;
        for (int tick = 0; tick < ticks; tick++) {
            executed += template.executeDue(tick, tick, 0, executor);
        }
        long elapsed = System.nanoTime() - start;

        if (executed != executor.count) {
            throw new IllegalStateException("executor count mismatch");
        }

        System.out.printf(
                "Exact execution template: %,d triggers x %,d ops, %,d ticks -> %,d op visits in %.3f ms (%.2f ns/op)%n",
                triggers,
                operationsPerTrigger,
                ticks,
                executed,
                elapsed / 1_000_000.0,
                elapsed / (double) executed
        );
    }

    private static final class CountingExecutor implements ExactExecutionTemplate.OperationExecutor {
        private int count;

        @Override
        public void execute(ExactOperation operation) {
            count++;
        }
    }
}
