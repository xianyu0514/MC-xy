package io.github.xianynomial.sfmfactorystudio.chronosfm;

import ca.teamdman.sfm.common.label.LabelPositionHolder;
import ca.teamdman.sfml.ast.Label;
import ca.teamdman.sfml.ast.LabelAccess;
import ca.teamdman.sfml.ast.NumberRangeSet;
import ca.teamdman.sfml.ast.RoundRobin;
import ca.teamdman.sfml.ast.Side;
import ca.teamdman.sfml.ast.SideQualifier;
import com.mojang.datafixers.util.Pair;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SfmCapabilityRouteCacheTest {
    @Test
    void allSfmSideModesAreStructurallyCacheable() {
        for (Side side : Side.values()) {
            assertTrue(SfmCapabilityRouteCache.isStructurallyCacheable(new LabelAccess(
                    List.of(new Label("machines")),
                    new SideQualifier(List.of(side)),
                    NumberRangeSet.MAX_RANGE,
                    RoundRobin.disabled()
            )));
        }
    }

    @Test
    void emptyLabelSetFailsClosed() {
        assertFalse(SfmCapabilityRouteCache.isStructurallyCacheable(new LabelAccess(
                List.of(),
                SideQualifier.DEFAULT,
                NumberRangeSet.MAX_RANGE,
                RoundRobin.disabled()
        )));
    }

    @Test
    void unmodifiedTemplateMatchesUpstreamLabelPositionOrder() {
        LabelPositionHolder holder = sampleHolder();
        LabelAccess upstream = access(RoundRobin.Behaviour.UNMODIFIED);
        LabelAccess cached = access(RoundRobin.Behaviour.UNMODIFIED);

        assertEquals(
                simplifyPairs(upstream.getLabelledPositions(holder)),
                simplifyCandidates(SfmCapabilityRouteCache.selectCandidatesForTesting(cached, holder))
        );
    }

    @Test
    void roundRobinByLabelMatchesUpstreamAcrossRepeatedCalls() {
        LabelPositionHolder holder = sampleHolder();
        LabelAccess upstream = access(RoundRobin.Behaviour.BY_LABEL);
        LabelAccess cached = access(RoundRobin.Behaviour.BY_LABEL);

        for (int i = 0; i < 12; i++) {
            assertEquals(
                    simplifyPairs(upstream.getLabelledPositions(holder)),
                    simplifyCandidates(SfmCapabilityRouteCache.selectCandidatesForTesting(cached, holder)),
                    "mismatch on round " + i
            );
        }
    }

    @Test
    void roundRobinByBlockMatchesUpstreamIncludingCrossLabelDeduplication() {
        LabelPositionHolder holder = sampleHolder();
        LabelAccess upstream = access(RoundRobin.Behaviour.BY_BLOCK);
        LabelAccess cached = access(RoundRobin.Behaviour.BY_BLOCK);

        for (int i = 0; i < 12; i++) {
            assertEquals(
                    simplifyPairs(upstream.getLabelledPositions(holder)),
                    simplifyCandidates(SfmCapabilityRouteCache.selectCandidatesForTesting(cached, holder)),
                    "mismatch on round " + i
            );
        }
    }

    private static LabelPositionHolder sampleHolder() {
        BlockPos shared = new BlockPos(9, 9, 9);
        return LabelPositionHolder.empty()
                .add("a", new BlockPos(1, 2, 3))
                .add("a", shared)
                .add("b", new BlockPos(4, 5, 6))
                .add("b", shared)
                .add("c", new BlockPos(7, 8, 9));
    }

    private static LabelAccess access(RoundRobin.Behaviour behaviour) {
        return new LabelAccess(
                List.of(new Label("a"), new Label("b"), new Label("c")),
                new SideQualifier(List.of(Side.FRONT, Side.NORTH, Side.NULL)),
                NumberRangeSet.MAX_RANGE,
                new RoundRobin(behaviour)
        );
    }

    private static List<String> simplifyPairs(List<Pair<Label, BlockPos>> pairs) {
        ArrayList<String> result = new ArrayList<>();
        for (Pair<Label, BlockPos> pair : pairs) {
            result.add(pair.getFirst().name() + "@" + pair.getSecond().asLong());
        }
        return result;
    }

    private static List<String> simplifyCandidates(
            List<SfmCapabilityRouteCache.RouteCandidate> candidates
    ) {
        ArrayList<String> result = new ArrayList<>();
        for (var candidate : candidates) {
            result.add(candidate.label().name() + "@" + candidate.position().asLong());
        }
        return result;
    }
}
