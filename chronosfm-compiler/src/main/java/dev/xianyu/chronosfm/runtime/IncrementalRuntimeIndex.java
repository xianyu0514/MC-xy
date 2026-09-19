package dev.xianyu.chronosfm.runtime;

import dev.xianyu.chronosfm.ir.DependencyIndex;

import java.util.Objects;

public final class IncrementalRuntimeIndex {
    private final PersistentEndpointIndex endpoints = new PersistentEndpointIndex();
    private final InvalidationEngine invalidation;

    public IncrementalRuntimeIndex(DependencyIndex dependencies) {
        this.invalidation = new InvalidationEngine(
                Objects.requireNonNull(dependencies, "dependencies")
        );
    }

    public PersistentEndpointIndex.EndpointDelta upsertEndpoint(EndpointDescriptor endpoint) {
        var delta = endpoints.upsert(endpoint);
        if (!delta.changed()) return delta;

        if (delta.previous() != null) invalidation.invalidateEndpoint(delta.previous());
        if (delta.current() != null) invalidation.invalidateEndpoint(delta.current());
        return delta;
    }

    public PersistentEndpointIndex.EndpointDelta removeEndpoint(long endpointId) {
        var delta = endpoints.remove(endpointId);
        if (delta.changed() && delta.previous() != null) {
            invalidation.invalidateEndpoint(delta.previous());
        }
        return delta;
    }

    /**
     * Marks dynamic state as changed without changing structural membership.
     * This is the hot path for inventory/capacity revisions.
     */
    public boolean endpointStateChanged(long endpointId) {
        var endpoint = endpoints.get(endpointId);
        if (endpoint.isEmpty()) return false;
        invalidation.invalidateEndpoint(endpoint.get());
        return true;
    }

    public PersistentEndpointIndex endpoints() {
        return endpoints;
    }

    public int dirtyCount() {
        return invalidation.dirtyCount();
    }

    public int[] drainDirtyRegions() {
        return invalidation.drainDirtyRegions();
    }
}
