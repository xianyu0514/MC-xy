package dev.xianyu.chronosfm;

import dev.xianyu.chronosfm.compiler.ChronoSfmCompiler;
import dev.xianyu.chronosfm.model.ProgramModel;
import dev.xianyu.chronosfm.model.StatementModel;
import dev.xianyu.chronosfm.model.TriggerModel;
import dev.xianyu.chronosfm.runtime.EndpointDescriptor;
import dev.xianyu.chronosfm.runtime.IncrementalRuntimeIndex;
import dev.xianyu.chronosfm.runtime.PersistentEndpointIndex;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class IncrementalIndexTest {
    private final ChronoSfmCompiler compiler = new ChronoSfmCompiler();

    @Test
    void allCompiledWorkUsesOneCollisionFreeRegionIdSpace() {
        var selector = new StatementModel.EndpointSelector(
                List.of("input"), List.of("north"), "0", "DEFAULT"
        );
        var resources = new StatementModel.ResourceSelector(
                List.of("item:iron"), "iron"
        );

        var plan = compiler.compile(new ProgramModel(List.of(
                new TriggerModel.Timer(1, TriggerModel.Alignment.LOCAL, 0, List.of(
                        new StatementModel.Transfer("a", "b", "item:iron", false, 64, 0),
                        new StatementModel.Input(selector, resources, false),
                        new StatementModel.Output(selector, resources, false, false),
                        new StatementModel.Opaque("barrier")
                ))
        )));

        Set<Integer> ids = new HashSet<>();
        plan.transferRegions().forEach(region -> assertTrue(ids.add(region.regionId())));
        plan.exactOperations().forEach(operation -> assertTrue(ids.add(operation.regionId())));

        assertEquals(plan.workRegionCount(), ids.size());
        assertEquals(4, plan.maxRegionIdExclusive());
        assertEquals(Set.of(0, 1, 2, 3), ids);
    }

    @Test
    void endpointIndexUpdatesMembershipWithoutStaleEntries() {
        var index = new PersistentEndpointIndex();
        index.upsert(new EndpointDescriptor(7, Set.of("old"), Set.of("item:iron"), 0));

        assertArrayEquals(new long[]{7}, index.endpointIdsForLabel("old"));
        assertArrayEquals(new long[]{7}, index.endpointIdsForResource("item:iron"));

        index.upsert(new EndpointDescriptor(7, Set.of("new"), Set.of("fluid:water"), 1));

        assertEquals(0, index.endpointIdsForLabel("old").length);
        assertEquals(0, index.endpointIdsForResource("item:iron").length);
        assertArrayEquals(new long[]{7}, index.endpointIdsForLabel("new"));
        assertArrayEquals(new long[]{7}, index.endpointIdsForResource("fluid:water"));
    }

    @Test
    void endpointStateChangeInvalidatesOnlyCompatibleLabelResourceRegions() {
        var plan = compiler.compile(new ProgramModel(List.of(
                new TriggerModel.Timer(1, TriggerModel.Alignment.LOCAL, 0, List.of(
                        new StatementModel.Transfer("source-a", "machine", "item:iron", false, 64, 0),
                        new StatementModel.Transfer("source-b", "machine", "fluid:water", false, 64, 0),
                        new StatementModel.Transfer("source-c", "other", "item:iron", false, 64, 0)
                ))
        )));

        var runtime = new IncrementalRuntimeIndex(plan.dependencyIndex());
        runtime.upsertEndpoint(new EndpointDescriptor(
                11, Set.of("machine"), Set.of("item:iron"), 0
        ));
        assertArrayEquals(new int[]{0}, runtime.drainDirtyRegions());

        assertTrue(runtime.endpointStateChanged(11));
        assertArrayEquals(new int[]{0}, runtime.drainDirtyRegions());
    }

    @Test
    void endpointMetadataChangeInvalidatesOldAndNewDependencyFrontiers() {
        var plan = compiler.compile(new ProgramModel(List.of(
                new TriggerModel.Timer(1, TriggerModel.Alignment.LOCAL, 0, List.of(
                        new StatementModel.Transfer("source-a", "old", "item:iron", false, 64, 0),
                        new StatementModel.Transfer("source-b", "new", "fluid:water", false, 64, 0),
                        new StatementModel.Transfer("source-c", "unrelated", "item:iron", false, 64, 0)
                ))
        )));

        var runtime = new IncrementalRuntimeIndex(plan.dependencyIndex());
        runtime.upsertEndpoint(new EndpointDescriptor(
                99, Set.of("old"), Set.of("item:iron"), 0
        ));
        assertArrayEquals(new int[]{0}, runtime.drainDirtyRegions());

        runtime.upsertEndpoint(new EndpointDescriptor(
                99, Set.of("new"), Set.of("fluid:water"), 1
        ));
        assertArrayEquals(new int[]{0, 1}, runtime.drainDirtyRegions());
    }

    @Test
    void unknownEndpointResourcesFallBackToConservativeLabelInvalidation() {
        var plan = compiler.compile(new ProgramModel(List.of(
                new TriggerModel.Timer(1, TriggerModel.Alignment.LOCAL, 0, List.of(
                        new StatementModel.Transfer("a", "shared", "item:iron", false, 64, 0),
                        new StatementModel.Transfer("b", "shared", "fluid:water", false, 64, 0)
                ))
        )));

        var runtime = new IncrementalRuntimeIndex(plan.dependencyIndex());
        runtime.upsertEndpoint(new EndpointDescriptor(1, Set.of("shared"), Set.of(), 0));

        assertArrayEquals(new int[]{0, 1}, runtime.drainDirtyRegions());
    }
}
