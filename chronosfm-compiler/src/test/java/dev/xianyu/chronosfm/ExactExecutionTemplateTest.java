package dev.xianyu.chronosfm;

import dev.xianyu.chronosfm.compiler.ChronoSfmCompiler;
import dev.xianyu.chronosfm.ir.ExactOperation;
import dev.xianyu.chronosfm.model.ProgramModel;
import dev.xianyu.chronosfm.model.StatementModel;
import dev.xianyu.chronosfm.model.TriggerModel;
import dev.xianyu.chronosfm.runtime.ExactExecutionTemplate;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ExactExecutionTemplateTest {
    private static StatementModel.Input input(String label) {
        return new StatementModel.Input(
                new StatementModel.EndpointSelector(
                        List.of(label), List.of(), "ALL", "UNMODIFIED"
                ),
                new StatementModel.ResourceSelector(List.of(), ""),
                false
        );
    }

    private static StatementModel.Output output(String label) {
        return new StatementModel.Output(
                new StatementModel.EndpointSelector(
                        List.of(label), List.of(), "ALL", "UNMODIFIED"
                ),
                new StatementModel.ResourceSelector(List.of(), ""),
                false,
                false
        );
    }

    @Test
    void precompiledTemplatePreservesTriggerAndStatementOrder() {
        var plan = new ChronoSfmCompiler().compile(new ProgramModel(List.of(
                new TriggerModel.Timer(
                        1, TriggerModel.Alignment.LOCAL, 0,
                        List.of(input("a"), output("b"))
                ),
                new TriggerModel.Timer(
                        1, TriggerModel.Alignment.LOCAL, 0,
                        List.of(input("c"), output("d"))
                )
        )));
        var template = new ExactExecutionTemplate(plan);
        List<Integer> order = new ArrayList<>();

        int count = template.executeDue(
                10, 10, 0,
                operation -> order.add(operation.exactOrderOrdinal())
        );

        assertEquals(4, count);
        assertEquals(List.of(0, 1, 2, 3), order);
    }

    @Test
    void redstoneTriggerRepeatsWholeBlockForEveryPulse() {
        var plan = new ChronoSfmCompiler().compile(new ProgramModel(List.of(
                new TriggerModel.Redstone(List.of(input("a"), output("b")))
        )));
        var template = new ExactExecutionTemplate(plan);
        List<Integer> order = new ArrayList<>();

        int count = template.executeDue(
                1, 1, 3,
                operation -> order.add(operation.exactOrderOrdinal())
        );

        assertEquals(6, count);
        assertEquals(List.of(0, 1, 0, 1, 0, 1), order);
    }

    @Test
    void unsupportedSemanticsFallbackBeforeAnyOperationExecutes() {
        var plan = new ChronoSfmCompiler().compile(new ProgramModel(List.of(
                new TriggerModel.Timer(
                        1, TriggerModel.Alignment.LOCAL, 0,
                        List.of(input("a"), new StatementModel.Opaque("if"))
                )
        )));
        var template = new ExactExecutionTemplate(plan);
        int[] called = {0};

        int result = template.executeDue(1, 1, 0, ignored -> called[0]++);

        assertTrue(template.requiresLegacy());
        assertEquals(ExactExecutionTemplate.LEGACY_REQUIRED, result);
        assertEquals(0, called[0]);
    }

    @Test
    void inactiveTimerTouchesNoOperations() {
        var plan = new ChronoSfmCompiler().compile(new ProgramModel(List.of(
                new TriggerModel.Timer(
                        20, TriggerModel.Alignment.LOCAL, 3,
                        List.of(input("a"), output("b"))
                )
        )));
        var template = new ExactExecutionTemplate(plan);
        int[] called = {0};

        assertEquals(0, template.executeDue(2, 2, 0, ignored -> called[0]++));
        assertEquals(0, called[0]);
        assertEquals(2, template.executeDue(3, 3, 0, ignored -> called[0]++));
        assertEquals(2, called[0]);
    }
}
