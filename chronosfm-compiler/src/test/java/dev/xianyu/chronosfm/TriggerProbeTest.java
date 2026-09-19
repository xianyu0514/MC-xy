package dev.xianyu.chronosfm;

import dev.xianyu.chronosfm.compiler.ChronoSfmCompiler;
import dev.xianyu.chronosfm.model.ProgramModel;
import dev.xianyu.chronosfm.model.TriggerModel;
import dev.xianyu.chronosfm.runtime.TriggerProbe;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TriggerProbeTest {
    @Test
    void inactiveTimerCanSkipExpensiveContext() {
        var program = new ProgramModel(List.of(
                new TriggerModel.Timer(20, TriggerModel.Alignment.LOCAL, 0, List.of())
        ));
        var probe = new TriggerProbe(new ChronoSfmCompiler().compile(program));

        assertTrue(probe.requiresFullContext(0, 0, 0));
        assertFalse(probe.maySkipFullContext(0, 0, 0));
        for (int tick = 1; tick < 20; tick++) {
            assertFalse(probe.requiresFullContext(tick, tick, 0));
            assertTrue(probe.maySkipFullContext(tick, tick, 0));
        }
    }

    @Test
    void redstoneOnlyRunsWhenPulseExists() {
        var program = new ProgramModel(List.of(new TriggerModel.Redstone(List.of())));
        var probe = new TriggerProbe(new ChronoSfmCompiler().compile(program));

        assertTrue(probe.maySkipFullContext(50, 50, 0));
        assertTrue(probe.requiresFullContext(50, 50, 1));
    }

    @Test
    void opaqueTriggerCanNeverBeSuppressedByFastGate() {
        var program = new ProgramModel(List.of(
                new TriggerModel.Opaque("custom trigger", List.of())
        ));
        var probe = new TriggerProbe(new ChronoSfmCompiler().compile(program));
        var result = probe.probe(123, 123, 0);

        assertTrue(probe.alwaysRequiresContext());
        assertTrue(probe.requiresFullContext(123, 123, 0));
        assertFalse(result.maySkipFullContext());
        assertTrue(result.requiresLegacyContext());
    }

    @Test
    void everyTickTimerUsesAlwaysDueFastPath() {
        var program = new ProgramModel(List.of(
                new TriggerModel.Timer(1, TriggerModel.Alignment.LOCAL, 0, List.of())
        ));
        var probe = new TriggerProbe(new ChronoSfmCompiler().compile(program));

        assertTrue(probe.alwaysRequiresContext());
        for (int tick = 0; tick < 100; tick++) {
            assertTrue(probe.requiresFullContext(tick, tick, 0));
        }
    }

    @Test
    void zeroAllocationDecisionMatchesDiagnosticProbe() {
        var program = new ProgramModel(List.of(
                new TriggerModel.Timer(7, TriggerModel.Alignment.LOCAL, 2, List.of()),
                new TriggerModel.Redstone(List.of())
        ));
        var probe = new TriggerProbe(new ChronoSfmCompiler().compile(program));

        for (int tick = 0; tick < 500; tick++) {
            int pulses = tick % 31 == 0 ? 1 : 0;
            assertEquals(
                    !probe.probe(tick, tick, pulses).maySkipFullContext(),
                    probe.requiresFullContext(tick, tick, pulses)
            );
        }
    }
}
