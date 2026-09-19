package dev.xianyu.chronosfm.runtime;

import dev.xianyu.chronosfm.ir.DependencyIndex;

import java.util.Objects;

public final class IncrementalRuntimeIndex {
    private final PersistentTransferGraph graph;

    public IncrementalRuntimeIndex(DependencyIndex dependencies) {
        this.graph = new PersistentTransferGraph(
                Objects.requireNonNull(dependencies, "dependencies")
        );
    }

    public PersistentEndpointIndex.EndpointDelta upsertEndpoint(EndpointDescriptor endpoint) {
        return graph.upsert(endpoint);
    }

    public PersistentEndpointIndex.EndpointDelta removeEndpoint(long endpointId) {
        return graph.remove(endpointId);
    }

    public boolean endpointStateChanged(long endpointId) {
        return graph.endpointStateChanged(endpointId);
    }

    public boolean endpointStateChanged(long endpointId, long nextRevision) {
        return graph.endpointStateChanged(endpointId, nextRevision);
    }

    public PersistentEndpointIndex endpoints() {
        return graph.endpoints();
    }

    public PersistentTransferGraph graph() {
        return graph;
    }

    public int dirtyCount() {
        return graph.dirtyCount();
    }

    public int[] drainDirtyRegions() {
        return graph.drainDirtyRegions();
    }
}
