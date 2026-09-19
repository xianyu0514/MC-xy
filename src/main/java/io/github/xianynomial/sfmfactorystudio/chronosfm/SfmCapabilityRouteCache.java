package io.github.xianynomial.sfmfactorystudio.chronosfm;

import ca.teamdman.sfm.common.capability.SFMBlockCapabilityResult;
import ca.teamdman.sfm.common.label.LabelPositionHolder;
import ca.teamdman.sfm.common.program.CapabilityConsumer;
import ca.teamdman.sfm.common.program.ProgramContext;
import ca.teamdman.sfm.common.resourcetype.ResourceType;
import ca.teamdman.sfml.ast.Label;
import ca.teamdman.sfml.ast.LabelAccess;
import ca.teamdman.sfml.ast.RoundRobin;
import ca.teamdman.sfml.ast.Side;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import org.apache.logging.log4j.Level;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.Consumer;

/**
 * Persistent structural route template for SFM capability discovery.
 *
 * One template is stored per LabelAccess, independent of ResourceType. The
 * template contains only stable label/position structure. It never retains a
 * third-party capability, stack, slot state, or insert/extract result.
 *
 * Round-robin semantics are preserved by advancing the original LabelAccess
 * RoundRobin object exactly once per ResourceType.forEachCapability invocation:
 * - UNMODIFIED: visit all cached label/position pairs
 * - BY_LABEL: choose one label, then visit all positions of that label
 * - BY_BLOCK: choose one deduplicated label/position candidate
 *
 * Relative sides remain dynamic: FRONT/BACK/LEFT/RIGHT are resolved from the
 * current BlockState on every due tick. Absolute sides avoid that world read.
 *
 * Diagnostic logging uses upstream SFM unchanged, so cached execution is only
 * used when the manager logger is OFF.
 */
public final class SfmCapabilityRouteCache {
    private static final Map<LabelAccess, Entry> CACHE =
            Collections.synchronizedMap(new WeakHashMap<>());

    private static long hitCount;
    private static long missCount;
    private static long bypassCount;

    private SfmCapabilityRouteCache() {
    }

    public static <STACK, ITEM, CAP> void forEachCapability(
            ResourceType<STACK, ITEM, CAP> resourceType,
            ProgramContext context,
            LabelAccess labelAccess,
            CapabilityConsumer<CAP> consumer
    ) {
        if (!canCache(context, labelAccess)) {
            bypassCount++;
            resourceType.forEachCapability(context, labelAccess, consumer);
            return;
        }

        Object labelsIdentity = context.getLabelPositionHolder();
        ChronoRevisionSource revisionSource =
                labelsIdentity instanceof ChronoRevisionSource source ? source : null;
        if (revisionSource == null) {
            bypassCount++;
            resourceType.forEachCapability(context, labelAccess, consumer);
            return;
        }

        long revision = revisionSource.chronosfm$getRevision();
        Entry entry = CACHE.get(labelAccess);
        if (entry == null
                || entry.labelHolderIdentity != labelsIdentity
                || entry.labelRevision != revision) {
            entry = new Entry(
                    labelsIdentity,
                    revision,
                    RouteTemplate.compile(labelAccess, context.getLabelPositionHolder())
            );
            CACHE.put(labelAccess, entry);
            missCount++;
        } else {
            hitCount++;
        }

        RouteTemplate template = entry.template;
        template.visitDueCandidates(labelAccess.roundRobin(), candidate ->
                visitCandidate(resourceType, context, labelAccess, candidate, consumer)
        );
    }

    private static <STACK, ITEM, CAP> void visitCandidate(
            ResourceType<STACK, ITEM, CAP> resourceType,
            ProgramContext context,
            LabelAccess labelAccess,
            RouteCandidate candidate,
            CapabilityConsumer<CAP> consumer
    ) {
        BlockState state = null;
        if (requiresBlockState(labelAccess)) {
            state = context.getLevel().getBlockState(candidate.position);
        }

        for (Side side : labelAccess.sides().sides()) {
            Direction direction = state == null
                    ? absoluteDirection(side)
                    : side.resolve(state);

            SFMBlockCapabilityResult<CAP> maybeCapability = context.getNetwork().getCapability(
                    resourceType.capabilityKind(),
                    candidate.position,
                    direction,
                    context.getLogger()
            );
            if (!maybeCapability.isPresent()) continue;

            consumer.accept(
                    candidate.label,
                    candidate.position,
                    direction,
                    maybeCapability.unwrap()
            );
        }
    }

    static boolean isStructurallyCacheable(LabelAccess labelAccess) {
        return labelAccess != null && !labelAccess.labels().isEmpty();
    }

    static List<RouteCandidate> selectCandidatesForTesting(
            LabelAccess labelAccess,
            LabelPositionHolder holder
    ) {
        RouteTemplate template = RouteTemplate.compile(labelAccess, holder);
        ArrayList<RouteCandidate> result = new ArrayList<>();
        template.visitDueCandidates(labelAccess.roundRobin(), result::add);
        return List.copyOf(result);
    }

    private static boolean canCache(ProgramContext context, LabelAccess labelAccess) {
        if (context == null
                || context.getLevel() == null
                || context.getLabelPositionHolder() == null
                || context.getNetwork() == null) {
            return false;
        }
        if (context.getLogger() != null && context.getLogger().getLogLevel() != Level.OFF) {
            return false;
        }
        return isStructurallyCacheable(labelAccess);
    }

    private static boolean requiresBlockState(LabelAccess labelAccess) {
        for (Side side : labelAccess.sides().sides()) {
            switch (side) {
                case LEFT, RIGHT, FRONT, BACK -> {
                    return true;
                }
                default -> {
                }
            }
        }
        return false;
    }

    private static Direction absoluteDirection(Side side) {
        return switch (side) {
            case TOP -> Direction.UP;
            case BOTTOM -> Direction.DOWN;
            case NORTH -> Direction.NORTH;
            case SOUTH -> Direction.SOUTH;
            case EAST -> Direction.EAST;
            case WEST -> Direction.WEST;
            case NULL -> null;
            case LEFT, RIGHT, FRONT, BACK ->
                    throw new IllegalArgumentException("relative side requires current BlockState: " + side);
        };
    }

    public static void clear() {
        CACHE.clear();
    }

    static int cachedTemplateCountForTesting() {
        return CACHE.size();
    }

    static long hitCountForTesting() {
        return hitCount;
    }

    static long missCountForTesting() {
        return missCount;
    }

    static long bypassCountForTesting() {
        return bypassCount;
    }

    static void resetMetricsForTesting() {
        hitCount = 0;
        missCount = 0;
        bypassCount = 0;
    }

    private record Entry(
            Object labelHolderIdentity,
            long labelRevision,
            RouteTemplate template
    ) {
    }

    record RouteCandidate(Label label, BlockPos position) {
    }

    private static final class RouteTemplate {
        private final RoundRobin.Behaviour behaviour;
        private final RouteCandidate[] allCandidates;
        private final LabelBucket[] labelBuckets;
        private final RouteCandidate[] deduplicatedCandidates;

        private RouteTemplate(
                RoundRobin.Behaviour behaviour,
                RouteCandidate[] allCandidates,
                LabelBucket[] labelBuckets,
                RouteCandidate[] deduplicatedCandidates
        ) {
            this.behaviour = behaviour;
            this.allCandidates = allCandidates;
            this.labelBuckets = labelBuckets;
            this.deduplicatedCandidates = deduplicatedCandidates;
        }

        static RouteTemplate compile(LabelAccess access, LabelPositionHolder holder) {
            RoundRobin.Behaviour behaviour = access.roundRobin().getBehaviour();
            List<Label> labels = access.labels();

            ArrayList<RouteCandidate> all = new ArrayList<>();
            LabelBucket[] buckets = new LabelBucket[labels.size()];
            HashSet<Long> seenPositions = new HashSet<>();
            ArrayList<RouteCandidate> deduplicated = new ArrayList<>();

            for (int labelIndex = 0; labelIndex < labels.size(); labelIndex++) {
                Label label = labels.get(labelIndex);
                ArrayList<RouteCandidate> perLabel = new ArrayList<>();
                var iterator = holder.getPositions(label.name()).blockPosIterator();
                while (iterator.hasNext()) {
                    BlockPos position = iterator.next().immutable();
                    RouteCandidate candidate = new RouteCandidate(label, position);
                    all.add(candidate);
                    perLabel.add(candidate);
                    if (seenPositions.add(position.asLong())) {
                        deduplicated.add(candidate);
                    }
                }
                buckets[labelIndex] = new LabelBucket(
                        label,
                        perLabel.toArray(RouteCandidate[]::new)
                );
            }

            return new RouteTemplate(
                    behaviour,
                    all.toArray(RouteCandidate[]::new),
                    buckets,
                    deduplicated.toArray(RouteCandidate[]::new)
            );
        }

        void visitDueCandidates(
                RoundRobin roundRobin,
                Consumer<RouteCandidate> consumer
        ) {
            switch (behaviour) {
                case UNMODIFIED -> {
                    for (RouteCandidate candidate : allCandidates) consumer.accept(candidate);
                }
                case BY_LABEL -> {
                    int count = labelBuckets.length;
                    if (count == 0) return;
                    int index = roundRobin.next(count);
                    RouteCandidate[] candidates = labelBuckets[index].candidates;
                    for (RouteCandidate candidate : candidates) consumer.accept(candidate);
                }
                case BY_BLOCK -> {
                    int count = deduplicatedCandidates.length;
                    if (count == 0) return;
                    consumer.accept(deduplicatedCandidates[roundRobin.next(count)]);
                }
            }
        }
    }

    private record LabelBucket(
            Label label,
            RouteCandidate[] candidates
    ) {
    }
}
