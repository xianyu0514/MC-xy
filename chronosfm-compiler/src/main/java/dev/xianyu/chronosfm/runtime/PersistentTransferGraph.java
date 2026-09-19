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
 * dependency index only when endpoint structure changes. Hot inventory/capacity
 * revisions mutate only a primitive revision field and mark the already-cached
 * region ids: no descriptor/binding allocation and no label/resource matching.
 */
public final class PersistentTransferGraph {
    private final DependencyIndex dependencies;
    private final PersistentEndpointIndex endpoints = new PersistentEndpointIndex();
    private final InvalidationEngine invalidation;
    private final Map<Long, BindingState> bindings = new HashMap<>();

    private long structuralRebindCount;
    private long hotStateUpdateCount;

    public PersistentTransferGraph(DependencyIndex dependencies) {
        this.dependencies = Objects.requireNonNull(dependencies, "dependencies");
        this.invalidation = new InvalidationEngine(dependencies);
    }

    public PersistentEndpointIndex.EndpointDelta upsert(EndpointDescriptor endpoint) {
        Objects.requireNonNull(endpoint, "endpoint");
        BindingState previousBinding = bindings.get(endpoint.endpointId());
        var delta = endpoints.upsert(endpoint);
        if (!delta.changed()) return delta;

        if (delta.structureChanged()) {
            int[] previousRegions = previousBinding == null
                    ? new int[0]
                    : previousBinding.dependentRegions;
            int[] nextRegions = dependencies.regionsForEndpoint(
                    endpoint.labels(),
                    endpoint.resourceTypes()
            );

            invalidation.invalidateRegions(previousRegions);
            invalidation.invalidateRegions(nextRegions);
            bindings.put(
                    endpoint.endpointId(),
                    new BindingState(endpoint, nextRegions)
            );
            structuralRebindCount++;
        } else {
            BindingState stable = Objects.requireNonNull(previousBinding, "stable binding");
            stable.revision = endpoint.revision();
            invalidation.invalidateRegions(stable.dependentRegions);
            hotStateUpdateCount++;
        }
        return delta;
    }

    public PersistentEndpointIndex.EndpointDelta remove(long endpointId) {
        BindingState previous = bindings.remove(endpointId);
        var delta = endpoints.remove(endpointId);
        if (previous != null) {
            invalidation.invalidateRegions(previous.dependentRegions);
            structuralRebindCount++;
        }
        return delta;
    }

    public boolean endpointStateChanged(long endpointId) {
        BindingState current = bindings.get(endpointId);
        if (current == null) return false;
        return endpointStateChanged(endpointId, current.revision + 1);
    }

    /**
     * Allocation-free steady-state update path.
     */
    public boolean endpointStateChanged(long endpointId, long nextRevision) {
        BindingState current = bindings.get(endpointId);
        if (current == null) return false;
        if (nextRevision < current.revision) {
            throw new IllegalArgumentException(
                    "endpoint revision cannot move backwards: "
                            + current.revision + " -> " + nextRevision
            );
        }
        if (nextRevision == current.revision) return false;

        current.revision = nextRevision;
        endpoints.updateRevision(endpointId, nextRevision);
        invalidation.invalidateRegions(current.dependentRegions);
        hotStateUpdateCount++;
        return true;
    }

    /**
     * Diagnostic snapshot. Not intended for the per-tick hot path.
     */
    public Optional<EndpointBinding> binding(long endpointId) {
        BindingState state = bindings.get(endpointId);
        if (state == null) return Optional.empty();
        return Optional.of(new EndpointBinding(
                new EndpointDescriptor(
                        endpointId,
                        state.labels,
                        state.resourceTypes,
                        state.revision
                ),
                state.dependentRegions
        ));
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

    private static final class BindingState {
        private final java.util.Set<String> labels;
        private final java.util.Set<String> resourceTypes;
        private final int[] dependentRegions;
        private long revision;

        private BindingState(EndpointDescriptor descriptor, int[] dependentRegions) {
            this.labels = java.util.Set.copyOf(descriptor.labels());
            this.resourceTypes = java.util.Set.copyOf(descriptor.resourceTypes());
            this.revision = descriptor.revision();
            this.dependentRegions = dependentRegions.clone();
        }
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
