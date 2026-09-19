package dev.xianyu.chronosfm.runtime;

import dev.xianyu.chronosfm.ir.DependencyIndex;

import java.util.BitSet;
import java.util.Objects;

public final class InvalidationEngine {
    private final DependencyIndex dependencies;
    private final BitSet dirtyRegions = new BitSet();

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
        for (int id : regionIds) {
            if (id < 0) throw new IllegalArgumentException("regionId must be >= 0");
            dirtyRegions.set(id);
        }
    }

    public void invalidateRegion(int regionId) {
        if (regionId < 0) throw new IllegalArgumentException("regionId must be >= 0");
        dirtyRegions.set(regionId);
    }

    public boolean isDirty(int regionId) {
        return dirtyRegions.get(regionId);
    }

    public int dirtyCount() {
        return dirtyRegions.cardinality();
    }

    public int[] drainDirtyRegions() {
        int[] result = dirtyRegions.stream().toArray();
        dirtyRegions.clear();
        return result;
    }
}
