package dev.xianyu.chronosfm.bench;

import dev.xianyu.chronosfm.compiler.ChronoSfmCompiler;
import dev.xianyu.chronosfm.ir.DependencyIndex;
import dev.xianyu.chronosfm.model.ProgramModel;
import dev.xianyu.chronosfm.model.StatementModel;
import dev.xianyu.chronosfm.model.TriggerModel;
import dev.xianyu.chronosfm.runtime.EndpointDescriptor;
import dev.xianyu.chronosfm.runtime.InvalidationEngine;
import dev.xianyu.chronosfm.runtime.PersistentTransferGraph;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

public final class BindingReuseBenchmark {
    private BindingReuseBenchmark() {
    }

    public static void main(String[] args) {
        int regions = args.length > 0 ? Integer.parseInt(args[0]) : 100_000;
        int changes = args.length > 1 ? Integer.parseInt(args[1]) : 10_000;
        int repetitions = args.length > 2 ? Integer.parseInt(args[2]) : 7;
        if (changes > regions) throw new IllegalArgumentException("changes must be <= regions");

        List<StatementModel> statements = new ArrayList<>(regions);
        EndpointDescriptor[] descriptors = new EndpointDescriptor[regions];
        for (int i = 0; i < regions; i++) {
            String resource = "item:" + (i % 128);
            String label = "dest-" + i;
            statements.add(new StatementModel.Transfer(
                    "source-" + i,
                    label,
                    resource,
                    false,
                    Long.MAX_VALUE,
                    0
            ));
            descriptors[i] = new EndpointDescriptor(
                    i, Set.of(label), Set.of(resource), 0
            );
        }

        var plan = new ChronoSfmCompiler().compile(new ProgramModel(List.of(
                new TriggerModel.Timer(1, TriggerModel.Alignment.LOCAL, 0, statements)
        )));
        DependencyIndex dependencies = plan.dependencyIndex();
        var graph = new PersistentTransferGraph(dependencies);
        var handles = new PersistentTransferGraph.EndpointHandle[regions];
        for (int i = 0; i < regions; i++) handles[i] = graph.bind(descriptors[i]);
        graph.drainDirtyRegions();

        int[] ids = new int[changes];
        for (int i = 0; i < changes; i++) {
            ids[i] = (int) (((long) i * 7_919L) % regions);
        }

        long[] cached = new long[repetitions];
        long[] recompute = new long[repetitions];

        for (int warm = 0; warm < 3; warm++) {
            runCached(graph, handles, ids);
            runRecompute(dependencies, descriptors, ids);
        }

        for (int rep = 0; rep < repetitions; rep++) {
            cached[rep] = runCached(graph, handles, ids);
            recompute[rep] = runRecompute(dependencies, descriptors, ids);
        }

        Arrays.sort(cached);
        Arrays.sort(recompute);
        long cachedMedian = cached[cached.length / 2];
        long recomputeMedian = recompute[recompute.length / 2];

        System.out.printf(
                "Binding reuse: %,d regions / %,d random hot changes -> dense-handle %.3f ms, dependency-recompute %.3f ms, ratio %.2fx%n",
                regions,
                changes,
                cachedMedian / 1_000_000.0,
                recomputeMedian / 1_000_000.0,
                recomputeMedian / (double) cachedMedian
        );
    }

    private static long runCached(
            PersistentTransferGraph graph,
            PersistentTransferGraph.EndpointHandle[] handles,
            int[] ids
    ) {
        long start = System.nanoTime();
        for (int id : ids) graph.endpointStateChanged(handles[id]);
        long elapsed = System.nanoTime() - start;

        if (graph.dirtyCount() != ids.length) {
            throw new IllegalStateException("cached dirty count mismatch");
        }
        graph.drainDirtyRegions();
        return elapsed;
    }

    private static long runRecompute(
            DependencyIndex dependencies,
            EndpointDescriptor[] descriptors,
            int[] ids
    ) {
        var invalidation = new InvalidationEngine(dependencies);
        long start = System.nanoTime();
        for (int id : ids) {
            EndpointDescriptor descriptor = descriptors[id];
            invalidation.invalidateRegions(dependencies.regionsForEndpoint(
                    descriptor.labels(),
                    descriptor.resourceTypes()
            ));
        }
        long elapsed = System.nanoTime() - start;

        if (invalidation.dirtyCount() != ids.length) {
            throw new IllegalStateException("recompute dirty count mismatch");
        }
        invalidation.drainDirtyRegions();
        return elapsed;
    }
}
