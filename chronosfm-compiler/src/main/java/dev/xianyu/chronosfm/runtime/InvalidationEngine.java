package dev.xianyu.chronosfm.runtime;

import dev.xianyu.chronosfm.ir.DependencyIndex;

import java.util.Arrays;
import java.util.BitSet;
import java.util.Objects;

/**
 * Sparse dirty frontier.
 *
 * BitSet is used only for O(1) deduplication. A compact int array records the
 * actual dirty ids, so draining K changed regions is O(K) rather than scanning
 * BitSet words up to the largest region id. This matters when a tiny change
 * touches a high-numbered region in a million-region compiled graph.
 */
public final class InvalidationEngine {
    private final DependencyIndex dependencies;
    private final BitSet dirtyMembership = new BitSet();

    private int[] dirtyIds = new int[32];
    private int dirtySize;

    public InvalidationEngine(DependencyIndex dependencies) {
        this.dependencies = Objects.requireNonNull(dependencies, "dependencies");
    }

    public void invalidateLabel(String label) {
        invalidateRegions(dependencies.regionsForLabel(label));
    }

    public void invalidateResource(String resourceKey) {
        invalidateRegions(dependencies.regionsForResource(resourceKey));
    }

    public void invalidateEndpoint(EndpointDescriptor endpoint) {
        Objects.requireNonNull(endpoint, "endpoint");
        invalidateRegions(dependencies.regionsForEndpoint(endpoint.labels(), endpoint.resourceTypes()));
    }

    public void invalidateRegions(int[] regionIds) {
        for (int id : regionIds) invalidateRegion(id);
    }

    public void invalidateRegion(int regionId) {
        if (regionId < 0) throw new IllegalArgumentException("regionId must be >= 0");
        if (dirtyMembership.get(regionId)) return;

        dirtyMembership.set(regionId);
        ensureCapacity(dirtySize + 1);
        dirtyIds[dirtySize++] = regionId;
    }

    public boolean isDirty(int regionId) {
        return dirtyMembership.get(regionId);
    }

    public int dirtyCount() {
        return dirtySize;
    }

    /**
     * Deterministic drain. Sorting is proportional to K dirty ids, not to graph
     * size. Later planner stages may add an unordered drain when ordering is
     * provably irrelevant.
     */
    public int[] drainDirtyRegions() {
        if (dirtySize == 0) return new int[0];

        int[] result = Arrays.copyOf(dirtyIds, dirtySize);
        Arrays.sort(result);

        for (int i = 0; i < dirtySize; i++) {
            dirtyMembership.clear(dirtyIds[i]);
        }
        dirtySize = 0;
        return result;
    }

    private void ensureCapacity(int required) {
        if (required <= dirtyIds.length) return;
        int next = Math.max(required, dirtyIds.length << 1);
        dirtyIds = Arrays.copyOf(dirtyIds, next);
    }
}
