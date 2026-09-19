package dev.xianyu.chronosfm.runtime;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

public final class PersistentEndpointIndex {
    private final Map<Long, EndpointDescriptor> byId = new HashMap<>();
    private final Map<String, Set<Long>> byLabel = new HashMap<>();
    private final Map<String, Set<Long>> byResource = new HashMap<>();

    public EndpointDelta upsert(EndpointDescriptor endpoint) {
        Objects.requireNonNull(endpoint, "endpoint");
        EndpointDescriptor previous = byId.get(endpoint.endpointId());
        if (endpoint.equals(previous)) {
            return new EndpointDelta(previous, endpoint, false, false);
        }

        boolean structureChanged = previous == null || !previous.hasSameStructure(endpoint);
        if (structureChanged && previous != null) removeMembership(previous);

        byId.put(endpoint.endpointId(), endpoint);

        if (structureChanged) addMembership(endpoint);
        return new EndpointDelta(previous, endpoint, true, structureChanged);
    }

    public EndpointDelta remove(long endpointId) {
        EndpointDescriptor previous = byId.remove(endpointId);
        if (previous == null) return new EndpointDelta(null, null, false, false);
        removeMembership(previous);
        return new EndpointDelta(previous, null, true, true);
    }

    public Optional<EndpointDescriptor> get(long endpointId) {
        return Optional.ofNullable(byId.get(endpointId));
    }

    public int size() {
        return byId.size();
    }

    public long[] endpointIdsForLabel(String label) {
        return sortedIds(byLabel.get(label));
    }

    public long[] endpointIdsForResource(String resourceType) {
        return sortedIds(byResource.get(resourceType));
    }

    private void addMembership(EndpointDescriptor endpoint) {
        for (String label : endpoint.labels()) {
            byLabel.computeIfAbsent(label, ignored -> new HashSet<>()).add(endpoint.endpointId());
        }
        for (String resource : endpoint.resourceTypes()) {
            byResource.computeIfAbsent(resource, ignored -> new HashSet<>()).add(endpoint.endpointId());
        }
    }

    private void removeMembership(EndpointDescriptor endpoint) {
        for (String label : endpoint.labels()) remove(byLabel, label, endpoint.endpointId());
        for (String resource : endpoint.resourceTypes()) remove(byResource, resource, endpoint.endpointId());
    }

    private static void remove(Map<String, Set<Long>> index, String key, long endpointId) {
        Set<Long> ids = index.get(key);
        if (ids == null) return;
        ids.remove(endpointId);
        if (ids.isEmpty()) index.remove(key);
    }

    private static long[] sortedIds(Set<Long> ids) {
        if (ids == null || ids.isEmpty()) return new long[0];
        return ids.stream().mapToLong(Long::longValue).sorted().toArray();
    }

    public record EndpointDelta(
            EndpointDescriptor previous,
            EndpointDescriptor current,
            boolean changed,
            boolean structureChanged
    ) {
    }
}
