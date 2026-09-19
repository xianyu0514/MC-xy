package io.github.xianynomial.sfmfactorystudio.chronosfm;

import ca.teamdman.sfm.common.label.LabelPositionHolder;
import ca.teamdman.sfml.ast.Label;
import ca.teamdman.sfml.ast.LabelAccess;
import ca.teamdman.sfml.ast.NumberRangeSet;
import ca.teamdman.sfml.ast.RoundRobin;
import ca.teamdman.sfml.ast.Side;
import ca.teamdman.sfml.ast.SideQualifier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SfmCapabilityRouteCacheTest {
    @Test
    void absoluteSidesWithoutRoundRobinAreCacheable() {
        assertTrue(SfmCapabilityRouteCache.isStructurallyCacheable(new LabelAccess(
                List.of(new Label("machines")),
                new SideQualifier(List.of(Side.NORTH, Side.TOP, Side.NULL)),
                NumberRangeSet.MAX_RANGE,
                RoundRobin.disabled()
        )));
    }

    @Test
    void relativeSidesAreFailClosed() {
        assertFalse(SfmCapabilityRouteCache.isStructurallyCacheable(new LabelAccess(
                List.of(new Label("machines")),
                new SideQualifier(List.of(Side.FRONT)),
                NumberRangeSet.MAX_RANGE,
                RoundRobin.disabled()
        )));
    }

    @Test
    void roundRobinIsFailClosed() {
        assertFalse(SfmCapabilityRouteCache.isStructurallyCacheable(new LabelAccess(
                List.of(new Label("machines")),
                SideQualifier.DEFAULT,
                NumberRangeSet.MAX_RANGE,
                new RoundRobin(RoundRobin.Behaviour.BY_BLOCK)
        )));
    }

    @Test
    void routeTemplatePreservesLabelPositionAndSideOrder() {
        LabelPositionHolder holder = LabelPositionHolder.empty()
                .add("a", new BlockPos(1, 2, 3))
                .add("b", new BlockPos(4, 5, 6));

        LabelAccess access = new LabelAccess(
                List.of(new Label("a"), new Label("b")),
                new SideQualifier(List.of(Side.NORTH, Side.TOP)),
                NumberRangeSet.MAX_RANGE,
                RoundRobin.disabled()
        );

        var routes = SfmCapabilityRouteCache.buildRoutesForTesting(access, holder);

        assertEquals(4, routes.size());
        assertEquals("a", routes.get(0).label().name());
        assertEquals(Direction.NORTH, routes.get(0).direction());
        assertEquals(Direction.UP, routes.get(1).direction());
        assertEquals("b", routes.get(2).label().name());
        assertEquals(Direction.NORTH, routes.get(2).direction());
        assertEquals(Direction.UP, routes.get(3).direction());
    }
}
