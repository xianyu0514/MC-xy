package dev.xianyu.chronosfm.bench;

import dev.xianyu.chronosfm.compiler.ChronoSfmCompiler;
import dev.xianyu.chronosfm.model.ProgramModel;
import dev.xianyu.chronosfm.model.TriggerModel;
import dev.xianyu.chronosfm.runtime.TriggerProbe;

import java.util.List;

public final class TriggerGateBenchmark {
    private TriggerGateBenchmark() {
    }

    public static void main(String[] args) {
        int probes = args.length > 0 ? Integer.parseInt(args[0]) : 5_000_000;

        var probe = new TriggerProbe(new ChronoSfmCompiler().compile(
                new ProgramModel(List.of(
                        new TriggerModel.Timer(
                                20,
                                TriggerModel.Alignment.LOCAL,
                                3,
                                List.of()
                        ),
                        new TriggerModel.Redstone(List.of())
                ))
        ));

        int due = 0;
        for (int i = 0; i < 500_000; i++) {
            if (probe.requiresFullContext(i, i, 0)) due++;
        }

        long start = System.nanoTime();
        for (int i = 0; i < probes; i++) {
            if (probe.requiresFullContext(i, i, 0)) due++;
        }
        long elapsed = System.nanoTime() - start;

        if (due == 0) throw new IllegalStateException("benchmark was optimized away");
        System.out.printf(
                "Zero-allocation trigger gate: %,d probes in %.3f ms (%.2f ns/probe)%n",
                probes,
                elapsed / 1_000_000.0,
                elapsed / (double) probes
        );
    }
}
