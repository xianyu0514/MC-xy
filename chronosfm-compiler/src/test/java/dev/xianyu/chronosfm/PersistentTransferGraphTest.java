package dev.xianyu.chronosfm;

import dev.xianyu.chronosfm.compiler.ChronoSfmCompiler;
import dev.xianyu.chronosfm.model.ProgramModel;
import dev.xianyu.chronosfm.model.StatementModel;
import dev.xianyu.chronosfm.model.TriggerModel;
import dev.xianyu.chronosfm.runtime.EndpointDescriptor;
import dev.xianyu.chronosfm.runtime.PersistentTransferGraph;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class PersistentTransferGraphTest {
    private final ChronoSfmCompiler compiler = new ChronoSfmCompiler();

    private PersistentTransferGraph graph() {
        var plan = compiler.compile(new ProgramModel(List.of(
                new TriggerModel.Timer(1, TriggerModel.Alignment.LOCAL, 0, List.of(
                        new StatementModel.Transfer("source-a", "machine", "item:iron", false, 64, 0),
                        new StatementModel.Transfer("source-b", "machine", "fluid:water", false, 64, 0),
                        new StatementModel.Transfer("source-c", "other", "item:iron", false, 64, 0)
                ))
        )));
        return new PersistentTransferGraph(plan.dependencyIndex());
    }

    @Test
    void hotRevisionUpdateReusesStructuralBinding() {
        var graph = graph();
        graph.upsert(new EndpointDescriptor(
                9, Set.of("machine"), Set.of("item:iron"), 0
        ));
        assertArrayEquals(new int[]{0}, graph.drainDirtyRegions());
        assertEquals(1, graph.structuralRebindCount());

        assertTrue(graph.endpointStateChanged(9, 1));
        assertArrayEquals(new int[]{0}, graph.drainDirtyRegions());
        assertEquals(1, graph.structuralRebindCount());
        assertEquals(1, graph.hotStateUpdateCount());
        assertArrayEquals(new int[]{0}, graph.binding(9).orElseThrow().dependentRegions());
    }

    @Test
    void structureChangeRebindsAndInvalidatesOldAndNewRegions() {
        var graph = graph();
        graph.upsert(new EndpointDescriptor(
                10, Set.of("machine"), Set.of("item:iron"), 0
        ));
        graph.drainDirtyRegions();

        graph.upsert(new EndpointDescriptor(
                10, Set.of("machine"), Set.of("fluid:water"), 1
        ));

        assertArrayEquals(new int[]{0, 1}, graph.drainDirtyRegions());
        assertEquals(2, graph.structuralRebindCount());
        assertEquals(0, graph.hotStateUpdateCount());
        assertArrayEquals(new int[]{1}, graph.binding(10).orElseThrow().dependentRegions());
    }

    @Test
    void staleRevisionCannotOverwriteNewerEndpointState() {
        var graph = graph();
        graph.upsert(new EndpointDescriptor(
                11, Set.of("machine"), Set.of("item:iron"), 5
        ));
        graph.drainDirtyRegions();

        assertThrows(IllegalArgumentException.class, () -> graph.endpointStateChanged(11, 4));
        assertEquals(5, graph.binding(11).orElseThrow().descriptor().revision());
        assertEquals(0, graph.dirtyCount());
    }
}
