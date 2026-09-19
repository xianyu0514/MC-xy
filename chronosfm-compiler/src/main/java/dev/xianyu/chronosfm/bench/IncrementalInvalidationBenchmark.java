package dev.xianyu.chronosfm.bench;

import dev.xianyu.chronosfm.compiler.ChronoSfmCompiler;
import dev.xianyu.chronosfm.model.ProgramModel;
import dev.xianyu.chronosfm.model.StatementModel;
import dev.xianyu.chronosfm.model.TriggerModel;
import dev.xianyu.chronosfm.runtime.EndpointDescriptor;
import dev.xianyu.chronosfm.runtime.IncrementalRuntimeIndex;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public final class IncrementalInvalidationBenchmark {
    private IncrementalInvalidationBenchmark() {
    }

    public static void main(String[] args) {
        int regions = args.length > 0 ? Integer.parseInt(args[0]) : 100_000;
        int changes = args.length > 1 ? Integer.parseInt(args[1]) : 1_000;
        if (changes > regions) throw new IllegalArgumentException("changes must be <= regions");

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

        var program = new ProgramModel(List.of(
                new TriggerModel.Timer(1, TriggerModel.Alignment.LOCAL, 0, statements)
        ));
        var plan = new ChronoSfmCompiler().compile(program);
        var runtime = new IncrementalRuntimeIndex(plan.dependencyIndex());

        for (int i = 0; i < regions; i++) {
            runtime.upsertEndpoint(new EndpointDescriptor(
                    i,
                    Set.of("dest-" + i),
                    Set.of("item:" + (i % 128)),
                    0
            ));
        }
        runtime.drainDirtyRegions();

        long start = System.nanoTime();
        for (int i = 0; i < changes; i++) {
            runtime.endpointStateChanged(i);
        }
        long elapsed = System.nanoTime() - start;
        int dirty = runtime.dirtyCount();

        if (dirty != changes) {
            throw new IllegalStateException("Expected " + changes + " dirty regions, got " + dirty);
        }

        System.out.printf(
                "Incremental frontier: %,d total regions, %,d endpoint changes -> %,d dirty regions in %.3f ms (%.1f ns/change)%n",
                regions,
                changes,
                dirty,
                elapsed / 1_000_000.0,
                elapsed / (double) changes
        );
    }
}
