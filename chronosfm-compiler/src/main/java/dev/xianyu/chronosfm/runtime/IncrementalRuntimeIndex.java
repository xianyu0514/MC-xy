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

    public PersistentTransferGraph.EndpointHandle bindEndpoint(EndpointDescriptor endpoint) {
        return graph.bind(endpoint);
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

    public boolean endpointStateChanged(PersistentTransferGraph.EndpointHandle handle) {
        return graph.endpointStateChanged(handle);
    }

    public boolean endpointStateChanged(
            PersistentTransferGraph.EndpointHandle handle,
            long nextRevision
    ) {
        return graph.endpointStateChanged(handle, nextRevision);
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

    public int drainDirtyRegionsInto(int[] destination) {
        return graph.drainDirtyRegionsInto(destination);
    }
}
