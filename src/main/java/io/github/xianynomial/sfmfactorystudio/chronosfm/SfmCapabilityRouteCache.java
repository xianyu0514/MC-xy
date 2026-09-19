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
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import org.apache.logging.log4j.Level;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Persistent structural route template for SFM capability discovery.
 *
 * Production representation is allocation-stable and mode-specific:
 *
 * UNMODIFIED:
 *   Label[] + BlockPos[]
 *
 * ROUND ROBIN BY LABEL:
 *   one LabelBucket per label, each with BlockPos[]
 *
 * ROUND ROBIN BY BLOCK:
 *   deduplicated Label[] + BlockPos[]
 *
 * Exactly one of those representations is retained by a template, so a large
 * label topology is not duplicated two or three times merely to support modes
 * that the current LabelAccess never uses.
 *
 * No capability object, stack, slot state or transfer result is cached.
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
        template.visitDueCandidates(
                labelAccess.roundRobin(),
                (label, position) ->
                        visitCandidate(resourceType, context, labelAccess, label, position, consumer)
        );
    }

    private static <STACK, ITEM, CAP> void visitCandidate(
            ResourceType<STACK, ITEM, CAP> resourceType,
            ProgramContext context,
            LabelAccess labelAccess,
            Label label,
            BlockPos position,
            CapabilityConsumer<CAP> consumer
    ) {
        BlockState state = null;
        if (requiresBlockState(labelAccess)) {
            state = context.getLevel().getBlockState(position);
        }

        for (Side side : labelAccess.sides().sides()) {
            Direction direction = state == null
                    ? absoluteDirection(side)
                    : side.resolve(state);

            SFMBlockCapabilityResult<CAP> maybeCapability = context.getNetwork().getCapability(
                    resourceType.capabilityKind(),
                    position,
                    direction,
                    context.getLogger()
            );
            if (!maybeCapability.isPresent()) continue;

            consumer.accept(label, position, direction, maybeCapability.unwrap());
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
        template.visitDueCandidates(
                labelAccess.roundRobin(),
                (label, position) -> result.add(new RouteCandidate(label, position))
        );
        return List.copyOf(result);
    }

    static TemplateStats templateStatsForTesting(
            LabelAccess labelAccess,
            LabelPositionHolder holder
    ) {
        return RouteTemplate.compile(labelAccess, holder).stats();
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

    record TemplateStats(
            RoundRobin.Behaviour behaviour,
            int candidateCount,
            int retainedCandidateArrayCount
    ) {
    }

    @FunctionalInterface
    private interface CandidateConsumer {
        void accept(Label label, BlockPos position);
    }

    private static final class RouteTemplate {
        private final RoundRobin.Behaviour behaviour;

        // UNMODIFIED / BY_BLOCK only.
        private final Label[] labels;
        private final BlockPos[] positions;

        // BY_LABEL only.
        private final LabelBucket[] labelBuckets;

        private RouteTemplate(
                RoundRobin.Behaviour behaviour,
                Label[] labels,
                BlockPos[] positions,
                LabelBucket[] labelBuckets
        ) {
            this.behaviour = behaviour;
            this.labels = labels;
            this.positions = positions;
            this.labelBuckets = labelBuckets;
        }

        static RouteTemplate compile(LabelAccess access, LabelPositionHolder holder) {
            return switch (access.roundRobin().getBehaviour()) {
                case UNMODIFIED -> compileUnmodified(access, holder);
                case BY_LABEL -> compileByLabel(access, holder);
                case BY_BLOCK -> compileByBlock(access, holder);
            };
        }

        private static RouteTemplate compileUnmodified(
                LabelAccess access,
                LabelPositionHolder holder
        ) {
            ArrayList<Label> labels = new ArrayList<>();
            ArrayList<BlockPos> positions = new ArrayList<>();

            for (Label label : access.labels()) {
                var iterator = holder.getPositions(label.name()).blockPosIterator();
                while (iterator.hasNext()) {
                    labels.add(label);
                    positions.add(iterator.next().immutable());
                }
            }

            return new RouteTemplate(
                    RoundRobin.Behaviour.UNMODIFIED,
                    labels.toArray(Label[]::new),
                    positions.toArray(BlockPos[]::new),
                    null
            );
        }

        private static RouteTemplate compileByLabel(
                LabelAccess access,
                LabelPositionHolder holder
        ) {
            List<Label> sourceLabels = access.labels();
            LabelBucket[] buckets = new LabelBucket[sourceLabels.size()];

            for (int i = 0; i < sourceLabels.size(); i++) {
                Label label = sourceLabels.get(i);
                ArrayList<BlockPos> positions = new ArrayList<>();
                var iterator = holder.getPositions(label.name()).blockPosIterator();
                while (iterator.hasNext()) {
                    positions.add(iterator.next().immutable());
                }
                buckets[i] = new LabelBucket(label, positions.toArray(BlockPos[]::new));
            }

            return new RouteTemplate(
                    RoundRobin.Behaviour.BY_LABEL,
                    null,
                    null,
                    buckets
            );
        }

        private static RouteTemplate compileByBlock(
                LabelAccess access,
                LabelPositionHolder holder
        ) {
            ArrayList<Label> labels = new ArrayList<>();
            ArrayList<BlockPos> positions = new ArrayList<>();
            LongOpenHashSet seen = new LongOpenHashSet();

            for (Label label : access.labels()) {
                var iterator = holder.getPositions(label.name()).blockPosIterator();
                while (iterator.hasNext()) {
                    BlockPos position = iterator.next().immutable();
                    if (!seen.add(position.asLong())) continue;
                    labels.add(label);
                    positions.add(position);
                }
            }

            return new RouteTemplate(
                    RoundRobin.Behaviour.BY_BLOCK,
                    labels.toArray(Label[]::new),
                    positions.toArray(BlockPos[]::new),
                    null
            );
        }

        void visitDueCandidates(
                RoundRobin roundRobin,
                CandidateConsumer consumer
        ) {
            switch (behaviour) {
                case UNMODIFIED -> visitAll(consumer);
                case BY_LABEL -> {
                    int count = labelBuckets.length;
                    if (count == 0) return;
                    LabelBucket bucket = labelBuckets[roundRobin.next(count)];
                    for (BlockPos position : bucket.positions) {
                        consumer.accept(bucket.label, position);
                    }
                }
                case BY_BLOCK -> {
                    int count = positions.length;
                    if (count == 0) return;
                    int index = roundRobin.next(count);
                    consumer.accept(labels[index], positions[index]);
                }
            }
        }

        private void visitAll(CandidateConsumer consumer) {
            for (int i = 0; i < positions.length; i++) {
                consumer.accept(labels[i], positions[i]);
            }
        }

        TemplateStats stats() {
            return switch (behaviour) {
                case UNMODIFIED, BY_BLOCK ->
                        new TemplateStats(behaviour, positions.length, 1);
                case BY_LABEL -> {
                    int count = 0;
                    for (LabelBucket bucket : labelBuckets) count += bucket.positions.length;
                    yield new TemplateStats(behaviour, count, labelBuckets.length);
                }
            };
        }
    }

    private record LabelBucket(Label label, BlockPos[] positions) {
    }
}
