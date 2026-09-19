package io.github.xianynomial.sfmfactorystudio.chronosfm;

import ca.teamdman.sfm.common.capability.SFMBlockCapabilityResult;
import ca.teamdman.sfm.common.program.CapabilityConsumer;
import ca.teamdman.sfm.common.program.ProgramContext;
import ca.teamdman.sfm.common.resourcetype.ResourceType;
import ca.teamdman.sfml.ast.Label;
import ca.teamdman.sfml.ast.LabelAccess;
import ca.teamdman.sfml.ast.Side;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import org.apache.logging.log4j.Level;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Persistent structural route cache for SFM capability discovery.
 *
 * The cache intentionally stores only immutable route addresses
 * (label/position/absolute direction). It NEVER stores the third-party
 * capability object and NEVER stores slot contents. Every due tick still asks
 * SFM's own CableNetwork capability cache for the current capability, preserving
 * normal invalidation and insert/extract semantics.
 *
 * Cache use is restricted to semantics that can be proven independent of
 * current BlockState:
 * - no round robin
 * - only TOP/BOTTOM/NORTH/SOUTH/EAST/WEST/NULL
 * - logger OFF (diagnostic logging retains the exact upstream path)
 *
 * LabelPositionHolder revisions are provided by a conservative mixin. If the
 * revision interface is unavailable, the optimization fails closed to upstream
 * ResourceType.forEachCapability.
 */
public final class SfmCapabilityRouteCache {
    private static final Map<LabelAccess, IdentityHashMap<ResourceType<?, ?, ?>, Entry>> CACHE =
            new WeakHashMap<>();

    private static long hitCount;
    private static long missCount;
    private static long bypassCount;

    private SfmCapabilityRouteCache() {
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
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

        Object labels = context.getLabelPositionHolder();
        ChronoRevisionSource revisionSource =
                labels instanceof ChronoRevisionSource source ? source : null;
        if (revisionSource == null) {
            bypassCount++;
            resourceType.forEachCapability(context, labelAccess, consumer);
            return;
        }

        long revision = revisionSource.chronosfm$getRevision();
        Entry entry = getEntry(labelAccess, resourceType);
        if (entry == null
                || entry.labelHolderIdentity != labels
                || entry.labelRevision != revision) {
            RouteAddress[] routes = buildRoutes(labelAccess, context);
            entry = new Entry(labels, revision, routes);
            putEntry(labelAccess, resourceType, entry);
            missCount++;
        } else {
            hitCount++;
        }

        for (RouteAddress route : entry.routes) {
            SFMBlockCapabilityResult<CAP> maybeCapability = context.getNetwork().getCapability(
                    resourceType.capabilityKind(),
                    route.position,
                    route.direction,
                    context.getLogger()
            );
            if (!maybeCapability.isPresent()) continue;

            consumer.accept(
                    route.label,
                    route.position,
                    route.direction,
                    maybeCapability.unwrap()
            );
        }
    }

    static boolean isStructurallyCacheable(LabelAccess labelAccess) {
        if (labelAccess == null || labelAccess.roundRobin().isEnabled()) return false;
        for (Side side : labelAccess.sides().sides()) {
            if (!isAbsolute(side)) return false;
        }
        return true;
    }

    static List<RouteAddress> buildRoutesForTesting(
            LabelAccess labelAccess,
            ca.teamdman.sfm.common.label.LabelPositionHolder holder
    ) {
        return List.of(buildRoutes(labelAccess, holder));
    }

    private static boolean canCache(ProgramContext context, LabelAccess labelAccess) {
        if (context == null || context.getLabelPositionHolder() == null || context.getNetwork() == null) {
            return false;
        }
        if (context.getLogger() != null && context.getLogger().getLogLevel() != Level.OFF) {
            return false;
        }
        return isStructurallyCacheable(labelAccess);
    }

    private static RouteAddress[] buildRoutes(LabelAccess labelAccess, ProgramContext context) {
        return buildRoutes(labelAccess, context.getLabelPositionHolder());
    }

    private static RouteAddress[] buildRoutes(
            LabelAccess labelAccess,
            ca.teamdman.sfm.common.label.LabelPositionHolder holder
    ) {
        ArrayList<RouteAddress> routes = new ArrayList<>();
        for (var pair : labelAccess.getLabelledPositions(holder)) {
            Label label = pair.getFirst();
            BlockPos position = pair.getSecond().immutable();
            for (Side side : labelAccess.sides().sides()) {
                routes.add(new RouteAddress(label, position, absoluteDirection(side)));
            }
        }
        return routes.toArray(RouteAddress[]::new);
    }

    private static boolean isAbsolute(Side side) {
        return switch (side) {
            case TOP, BOTTOM, NORTH, SOUTH, EAST, WEST, NULL -> true;
            case LEFT, RIGHT, FRONT, BACK -> false;
        };
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
                    throw new IllegalArgumentException("relative side is not cacheable: " + side);
        };
    }

    private static Entry getEntry(
            LabelAccess access,
            ResourceType<?, ?, ?> resourceType
    ) {
        IdentityHashMap<ResourceType<?, ?, ?>, Entry> byType = CACHE.get(access);
        return byType == null ? null : byType.get(resourceType);
    }

    private static void putEntry(
            LabelAccess access,
            ResourceType<?, ?, ?> resourceType,
            Entry entry
    ) {
        CACHE.computeIfAbsent(access, ignored -> new IdentityHashMap<>())
                .put(resourceType, entry);
    }

    public static void clear() {
        CACHE.clear();
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
            RouteAddress[] routes
    ) {
    }

    record RouteAddress(
            Label label,
            BlockPos position,
            Direction direction
    ) {
    }
}
