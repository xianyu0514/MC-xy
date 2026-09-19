package dev.xianyu.chronosfm.bench;

import dev.xianyu.chronosfm.compiler.ChronoSfmCompiler;
import dev.xianyu.chronosfm.model.ProgramModel;
import dev.xianyu.chronosfm.model.TriggerModel;
import dev.xianyu.chronosfm.runtime.InvalidationEngine;

import java.util.List;

public final class SparseFrontierBenchmark {
    private SparseFrontierBenchmark() {
    }

    public static void main(String[] args) {
        int maxRegionId = args.length > 0 ? Integer.parseInt(args[0]) : 1_000_000;
        int repetitions = args.length > 1 ? Integer.parseInt(args[1]) : 200_000;

        var plan = new ChronoSfmCompiler().compile(new ProgramModel(List.of(
                new TriggerModel.Timer(1, TriggerModel.Alignment.LOCAL, 0, List.of())
        )));
        var engine = new InvalidationEngine(plan.dependencyIndex());

        long start = System.nanoTime();
        long checksum = 0;
        for (int i = 0; i < repetitions; i++) {
            int region = maxRegionId - (i & 7);
            engine.invalidateRegion(region);
            int[] dirty = engine.drainDirtyRegions();
            checksum += dirty[0];
        }
        long elapsed = System.nanoTime() - start;

        if (checksum == 0) throw new IllegalStateException("benchmark was optimized away");
        System.out.printf(
                "Sparse high-id frontier: %,d single-region invalidations near id %,d in %.3f ms (%.1f ns/cycle)%n",
                repetitions,
                maxRegionId,
                elapsed / 1_000_000.0,
                elapsed / (double) repetitions
        );
    }
}
