package dev.xianyu.chronosfm.runtime;

import dev.xianyu.chronosfm.ir.DependencyIndex;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Persistent endpoint -> work-region bindings.
 *
 * Structural metadata (labels/resource types) is resolved against the compiler
 * dependency index only when the endpoint structure changes. Hot inventory or
 * capacity revisions reuse the cached dependent region array, so the steady
 * state update path does not repeat label/resource matching.
 */
public final class PersistentTransferGraph {
    private final DependencyIndex dependencies;
    private final PersistentEndpointIndex endpoints = new PersistentEndpointIndex();
    private final InvalidationEngine invalidation;
    private final Map<Long, EndpointBinding> bindings = new HashMap<>();

    private long structuralRebindCount;
    private long hotStateUpdateCount;

    public PersistentTransferGraph(DependencyIndex dependencies) {
        this.dependencies = Objects.requireNonNull(dependencies, "dependencies");
        this.invalidation = new InvalidationEngine(dependencies);
    }

    public PersistentEndpointIndex.EndpointDelta upsert(EndpointDescriptor endpoint) {
        Objects.requireNonNull(endpoint, "endpoint");
        EndpointBinding previousBinding = bindings.get(endpoint.endpointId());
        var delta = endpoints.upsert(endpoint);
        if (!delta.changed()) return delta;

        if (delta.structureChanged()) {
            int[] previousRegions = previousBinding == null
                    ? new int[0]
                    : previousBinding.dependentRegions();
            int[] nextRegions = dependencies.regionsForEndpoint(
                    endpoint.labels(),
                    endpoint.resourceTypes()
            );

            invalidation.invalidateRegions(previousRegions);
            invalidation.invalidateRegions(nextRegions);
            bindings.put(endpoint.endpointId(), new EndpointBinding(endpoint, nextRegions));
            structuralRebindCount++;
        } else {
            int[] stableRegions = previousBinding == null
                    ? dependencies.regionsForEndpoint(endpoint.labels(), endpoint.resourceTypes())
                    : previousBinding.dependentRegions();
            invalidation.invalidateRegions(stableRegions);
            bindings.put(endpoint.endpointId(), new EndpointBinding(endpoint, stableRegions));
            hotStateUpdateCount++;
        }
        return delta;
    }

    public PersistentEndpointIndex.EndpointDelta remove(long endpointId) {
        EndpointBinding previous = bindings.remove(endpointId);
        var delta = endpoints.remove(endpointId);
        if (previous != null) {
            invalidation.invalidateRegions(previous.dependentRegions());
            structuralRebindCount++;
        }
        return delta;
    }

    public boolean endpointStateChanged(long endpointId) {
        EndpointBinding current = bindings.get(endpointId);
        if (current == null) return false;
        return endpointStateChanged(endpointId, current.descriptor().revision() + 1);
    }

    public boolean endpointStateChanged(long endpointId, long nextRevision) {
        EndpointBinding current = bindings.get(endpointId);
        if (current == null) return false;

        long currentRevision = current.descriptor().revision();
        if (nextRevision < currentRevision) {
            throw new IllegalArgumentException(
                    "endpoint revision cannot move backwards: "
                            + currentRevision + " -> " + nextRevision
            );
        }
        if (nextRevision == currentRevision) return false;

        EndpointDescriptor next = current.descriptor().withRevision(nextRevision);
        endpoints.upsert(next); // revision-only: no membership churn
        bindings.put(endpointId, new EndpointBinding(next, current.dependentRegions()));
        invalidation.invalidateRegions(current.dependentRegions());
        hotStateUpdateCount++;
        return true;
    }

    public Optional<EndpointBinding> binding(long endpointId) {
        return Optional.ofNullable(bindings.get(endpointId));
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

    public long structuralRebindCount() {
        return structuralRebindCount;
    }

    public long hotStateUpdateCount() {
        return hotStateUpdateCount;
    }

    public record EndpointBinding(
            EndpointDescriptor descriptor,
            int[] dependentRegions
    ) {
        public EndpointBinding {
            Objects.requireNonNull(descriptor, "descriptor");
            dependentRegions = dependentRegions.clone();
        }

        @Override
        public int[] dependentRegions() {
            return dependentRegions.clone();
        }
    }
}
