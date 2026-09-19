package dev.xianyu.chronosfm;

import dev.xianyu.chronosfm.compiler.ChronoSfmCompiler;
import dev.xianyu.chronosfm.model.ProgramModel;
import dev.xianyu.chronosfm.model.TriggerModel;
import dev.xianyu.chronosfm.runtime.InvalidationEngine;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SparseInvalidationFrontierTest {
    private static InvalidationEngine emptyEngine() {
        var plan = new ChronoSfmCompiler().compile(new ProgramModel(List.of(
                new TriggerModel.Timer(1, TriggerModel.Alignment.LOCAL, 0, List.of())
        )));
        return new InvalidationEngine(plan.dependencyIndex());
    }

    @Test
    void highRegionIdDoesNotRequireDenseDirtyPopulation() {
        var engine = emptyEngine();

        engine.invalidateRegion(1_000_000);
        assertEquals(1, engine.dirtyCount());
        assertTrue(engine.isDirty(1_000_000));
        assertArrayEquals(new int[]{1_000_000}, engine.drainDirtyRegions());
        assertEquals(0, engine.dirtyCount());
        assertFalse(engine.isDirty(1_000_000));
    }

    @Test
    void duplicateInvalidationsAreDeduplicatedWithoutChangingOrderResult() {
        var engine = emptyEngine();

        engine.invalidateRegion(999_999);
        engine.invalidateRegion(3);
        engine.invalidateRegion(999_999);
        engine.invalidateRegion(42);
        engine.invalidateRegion(3);

        assertEquals(3, engine.dirtyCount());
        assertArrayEquals(new int[]{3, 42, 999_999}, engine.drainDirtyRegions());
    }

    @Test
    void repeatedDrainReusesFrontierStorageSafely() {
        var engine = emptyEngine();

        for (int round = 0; round < 100; round++) {
            engine.invalidateRegion(900_000 + round);
            assertEquals(1, engine.dirtyCount());
            assertArrayEquals(
                    new int[]{900_000 + round},
                    engine.drainDirtyRegions()
            );
        }
    }
}
