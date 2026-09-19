package dev.xianyu.chronosfm.bench;

import dev.xianyu.chronosfm.compiler.ChronoSfmCompiler;
import dev.xianyu.chronosfm.model.ProgramModel;
import dev.xianyu.chronosfm.model.StatementModel;
import dev.xianyu.chronosfm.model.TriggerModel;
import dev.xianyu.chronosfm.runtime.EndpointDescriptor;
import dev.xianyu.chronosfm.runtime.PersistentTransferGraph;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

public final class ChurnSweepBenchmark {
    private static final double[] FRACTIONS = {
            0.0001, 0.001, 0.01, 0.05, 0.10, 0.50, 1.0
    };

    private ChurnSweepBenchmark() {
    }

    public static void main(String[] args) {
        int regions = args.length > 0 ? Integer.parseInt(args[0]) : 100_000;
        int repetitions = args.length > 1 ? Integer.parseInt(args[1]) : 5;

        List<StatementModel> statements = new ArrayList<>(regions);
        for (int i = 0; i < regions; i++) {
            statements.add(new StatementModel.Transfer(
                    "source-" + i,
                    "dest-" + i,
                    "item:" + (i % 128),
                    false,
                    Long.MAX_VALUE,
                    0
            ));
        }

        var plan = new ChronoSfmCompiler().compile(new ProgramModel(List.of(
                new TriggerModel.Timer(1, TriggerModel.Alignment.LOCAL, 0, statements)
        )));
        var graph = new PersistentTransferGraph(plan.dependencyIndex());
        var handles = new PersistentTransferGraph.EndpointHandle[regions];

        for (int i = 0; i < regions; i++) {
            handles[i] = graph.bind(new EndpointDescriptor(
                    i,
                    Set.of("dest-" + i),
                    Set.of("item:" + (i % 128)),
                    0
            ));
        }
        graph.drainDirtyRegions();

        System.out.printf("Churn sweep over %,d persistent regions (%d repetitions)%n", regions, repetitions);
        System.out.println("change%,changes,median_ms,ns_per_change,dirty_regions");

        for (double fraction : FRACTIONS) {
            int changes = Math.max(1, (int) Math.round(regions * fraction));
            long[] samples = new long[repetitions];
            int dirty = -1;

            for (int rep = 0; rep < repetitions; rep++) {
                int offset = (rep * 104_729) % regions;
                long start = System.nanoTime();
                for (int i = 0; i < changes; i++) {
                    int id = (int) (((long) i * 7_919L + offset) % regions);
                    graph.endpointStateChanged(handles[id]);
                }
                samples[rep] = System.nanoTime() - start;
                dirty = graph.dirtyCount();
                graph.drainDirtyRegions();
            }

            if (dirty != changes) {
                throw new IllegalStateException(
                        "Expected " + changes + " dirty regions, got " + dirty
                );
            }

            Arrays.sort(samples);
            long median = samples[samples.length / 2];
            System.out.printf(
                    "%.4f,%,d,%.3f,%.1f,%,d%n",
                    fraction * 100.0,
                    changes,
                    median / 1_000_000.0,
                    median / (double) changes,
                    dirty
            );
        }

        if (graph.structuralRebindCount() != regions) {
            throw new IllegalStateException("State churn rebuilt persistent structure");
        }
    }
}
