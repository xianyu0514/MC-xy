package dev.xianyu.chronosfm.runtime;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Persistent structural endpoint index.
 *
 * This class intentionally owns NO dynamic endpoint revision/state. It only
 * indexes stable structural membership (endpoint id -> labels/resource types).
 * Runtime revision is authoritative in PersistentTransferGraph.BindingState.
 *
 * Keeping structure and state separate avoids a second revision copy that would
 * otherwise need a boxed-id map lookup on every hot update.
 */
public final class PersistentEndpointIndex {
    private final Map<Long, Entry> byId = new HashMap<>();
    private final Map<String, Set<Long>> byLabel = new HashMap<>();
    private final Map<String, Set<Long>> byResource = new HashMap<>();

    public EndpointDelta upsert(EndpointDescriptor endpoint) {
        Objects.requireNonNull(endpoint, "endpoint");
        Entry entry = byId.get(endpoint.endpointId());

        if (entry != null
                && entry.labels.equals(endpoint.labels())
                && entry.resourceTypes.equals(endpoint.resourceTypes())) {
            return new EndpointDelta(false, false);
        }

        if (entry != null) removeMembership(entry);

        if (entry == null) {
            entry = new Entry(
                    endpoint.endpointId(),
                    endpoint.labels(),
                    endpoint.resourceTypes()
            );
            byId.put(endpoint.endpointId(), entry);
        } else {
            entry.labels = endpoint.labels();
            entry.resourceTypes = endpoint.resourceTypes();
        }

        addMembership(entry);
        return new EndpointDelta(true, true);
    }

    public EndpointDelta remove(long endpointId) {
        Entry previous = byId.remove(endpointId);
        if (previous == null) return new EndpointDelta(false, false);
        removeMembership(previous);
        return new EndpointDelta(true, true);
    }

    public int size() {
        return byId.size();
    }

    public boolean contains(long endpointId) {
        return byId.containsKey(endpointId);
    }

    public long[] endpointIdsForLabel(String label) {
        return sortedIds(byLabel.get(label));
    }

    public long[] endpointIdsForResource(String resourceType) {
        return sortedIds(byResource.get(resourceType));
    }

    private void addMembership(Entry endpoint) {
        for (String label : endpoint.labels) {
            byLabel.computeIfAbsent(label, ignored -> new HashSet<>()).add(endpoint.endpointId);
        }
        for (String resource : endpoint.resourceTypes) {
            byResource.computeIfAbsent(resource, ignored -> new HashSet<>()).add(endpoint.endpointId);
        }
    }

    private void removeMembership(Entry endpoint) {
        for (String label : endpoint.labels) remove(byLabel, label, endpoint.endpointId);
        for (String resource : endpoint.resourceTypes) remove(byResource, resource, endpoint.endpointId);
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

    private static final class Entry {
        private final long endpointId;
        private Set<String> labels;
        private Set<String> resourceTypes;

        private Entry(
                long endpointId,
                Set<String> labels,
                Set<String> resourceTypes
        ) {
            this.endpointId = endpointId;
            this.labels = Set.copyOf(labels);
            this.resourceTypes = Set.copyOf(resourceTypes);
        }
    }

    public record EndpointDelta(
            boolean changed,
            boolean structureChanged
    ) {
    }
}
