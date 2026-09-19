package dev.xianyu.chronosfm.runtime;

import dev.xianyu.chronosfm.ir.DependencyIndex;

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Persistent endpoint -> work-region bindings.
 *
 * Structural lookup uses the external long endpoint id. Once bound, the hot
 * path uses a dense EndpointHandle and array access, avoiding Long boxing and
 * HashMap lookup on every inventory/capacity revision.
 */
public final class PersistentTransferGraph {
    private final DependencyIndex dependencies;
    private final PersistentEndpointIndex endpoints = new PersistentEndpointIndex();
    private final InvalidationEngine invalidation;

    // Structural path only.
    private final Map<Long, Integer> slotByEndpointId = new HashMap<>();
    private final ArrayDeque<Integer> freeSlots = new ArrayDeque<>();

    // Hot path.
    private BindingState[] bindingsBySlot = new BindingState[64];
    private int nextSlot;
    private long nextGeneration = 1;

    private long structuralRebindCount;
    private long hotStateUpdateCount;

    public PersistentTransferGraph(DependencyIndex dependencies) {
        this.dependencies = Objects.requireNonNull(dependencies, "dependencies");
        this.invalidation = new InvalidationEngine(dependencies);
    }

    /**
     * Structural bind/update. Retain the returned handle for subsequent hot
     * state changes.
     */
    public EndpointHandle bind(EndpointDescriptor endpoint) {
        upsert(endpoint);
        Integer slot = slotByEndpointId.get(endpoint.endpointId());
        if (slot == null) throw new IllegalStateException("endpoint was not bound");
        BindingState state = bindingsBySlot[slot];
        return new EndpointHandle(slot, endpoint.endpointId(), state.generation);
    }

    public PersistentEndpointIndex.EndpointDelta upsert(EndpointDescriptor endpoint) {
        Objects.requireNonNull(endpoint, "endpoint");
        Integer slotObject = slotByEndpointId.get(endpoint.endpointId());
        BindingState previousBinding = slotObject == null ? null : bindingsBySlot[slotObject];

        var structuralDelta = endpoints.upsert(endpoint);

        int slot;
        if (slotObject == null) {
            slot = acquireSlot();
            slotByEndpointId.put(endpoint.endpointId(), slot);
        } else {
            slot = slotObject;
        }

        if (previousBinding != null && endpoint.revision() < previousBinding.revision) {
            throw new IllegalArgumentException(
                    "endpoint revision cannot move backwards: "
                            + previousBinding.revision + " -> " + endpoint.revision()
            );
        }

        if (structuralDelta.structureChanged()) {
            int[] previousRegions = previousBinding == null
                    ? new int[0]
                    : previousBinding.dependentRegions;
            int[] nextRegions = dependencies.regionsForEndpoint(
                    endpoint.labels(),
                    endpoint.resourceTypes()
            );

            invalidation.invalidateRegions(previousRegions);
            invalidation.invalidateRegions(nextRegions);
            bindingsBySlot[slot] = new BindingState(
                    endpoint,
                    nextRegions,
                    nextGeneration++
            );
            structuralRebindCount++;
            return new PersistentEndpointIndex.EndpointDelta(true, true);
        }

        BindingState stable = Objects.requireNonNull(previousBinding, "stable binding");
        if (endpoint.revision() == stable.revision) {
            return new PersistentEndpointIndex.EndpointDelta(false, false);
        }

        stable.revision = endpoint.revision();
        invalidation.invalidateRegions(stable.dependentRegions);
        hotStateUpdateCount++;
        return new PersistentEndpointIndex.EndpointDelta(true, false);
    }

    public PersistentEndpointIndex.EndpointDelta remove(long endpointId) {
        Integer slot = slotByEndpointId.remove(endpointId);
        BindingState previous = slot == null ? null : bindingsBySlot[slot];
        var delta = endpoints.remove(endpointId);

        if (previous != null) {
            invalidation.invalidateRegions(previous.dependentRegions);
            bindingsBySlot[slot] = null;
            freeSlots.addLast(slot);
            structuralRebindCount++;
        }
        return delta;
    }

    /**
     * Compatibility path for callers that have not retained a dense handle.
     * Structural adapters should prefer bind()+EndpointHandle.
     */
    public boolean endpointStateChanged(long endpointId) {
        Integer slot = slotByEndpointId.get(endpointId);
        if (slot == null) return false;
        BindingState current = bindingsBySlot[slot];
        return endpointStateChanged(
                new EndpointHandle(slot, endpointId, current.generation)
        );
    }

    public boolean endpointStateChanged(EndpointHandle handle) {
        BindingState current = validateHandle(handle);
        if (current == null) return false;
        return endpointStateChanged(handle, current.revision + 1);
    }

    /**
     * Allocation-free, boxing-free steady-state update path.
     */
    public boolean endpointStateChanged(EndpointHandle handle, long nextRevision) {
        BindingState current = validateHandle(handle);
        if (current == null) return false;

        if (nextRevision < current.revision) {
            throw new IllegalArgumentException(
                    "endpoint revision cannot move backwards: "
                            + current.revision + " -> " + nextRevision
            );
        }
        if (nextRevision == current.revision) return false;

        current.revision = nextRevision;
        invalidation.invalidateRegions(current.dependentRegions);
        hotStateUpdateCount++;
        return true;
    }

    public Optional<EndpointHandle> handle(long endpointId) {
        Integer slot = slotByEndpointId.get(endpointId);
        if (slot == null) return Optional.empty();
        BindingState state = bindingsBySlot[slot];
        if (state == null) return Optional.empty();
        return Optional.of(new EndpointHandle(slot, endpointId, state.generation));
    }

    /**
     * Diagnostic snapshot. Not intended for the per-tick hot path.
     */
    public Optional<EndpointBinding> binding(long endpointId) {
        var handle = handle(endpointId);
        return handle.flatMap(this::binding);
    }

    public Optional<EndpointBinding> binding(EndpointHandle handle) {
        BindingState state = validateHandle(handle);
        if (state == null) return Optional.empty();

        return Optional.of(new EndpointBinding(
                new EndpointDescriptor(
                        state.endpointId,
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

    public int drainDirtyRegionsInto(int[] destination) {
        return invalidation.drainDirtyRegionsInto(destination);
    }

    public long structuralRebindCount() {
        return structuralRebindCount;
    }

    public long hotStateUpdateCount() {
        return hotStateUpdateCount;
    }

    private BindingState validateHandle(EndpointHandle handle) {
        Objects.requireNonNull(handle, "handle");
        int slot = handle.slot();
        if (slot < 0 || slot >= bindingsBySlot.length) return null;
        BindingState state = bindingsBySlot[slot];
        if (state == null) return null;
        if (state.endpointId != handle.endpointId()) return null;
        if (state.generation != handle.generation()) return null;
        return state;
    }

    private int acquireSlot() {
        Integer recycled = freeSlots.pollFirst();
        if (recycled != null) return recycled;

        int slot = nextSlot++;
        if (slot >= bindingsBySlot.length) {
            bindingsBySlot = Arrays.copyOf(bindingsBySlot, bindingsBySlot.length << 1);
        }
        return slot;
    }

    private static final class BindingState {
        private final long endpointId;
        private final java.util.Set<String> labels;
        private final java.util.Set<String> resourceTypes;
        private final int[] dependentRegions;
        private final long generation;
        private long revision;

        private BindingState(
                EndpointDescriptor descriptor,
                int[] dependentRegions,
                long generation
        ) {
            this.endpointId = descriptor.endpointId();
            this.labels = java.util.Set.copyOf(descriptor.labels());
            this.resourceTypes = java.util.Set.copyOf(descriptor.resourceTypes());
            this.revision = descriptor.revision();
            this.dependentRegions = dependentRegions.clone();
            this.generation = generation;
        }
    }

    public record EndpointHandle(
            int slot,
            long endpointId,
            long generation
    ) {
        public EndpointHandle {
            if (slot < 0 || endpointId < 0 || generation <= 0) {
                throw new IllegalArgumentException("invalid endpoint handle");
            }
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
