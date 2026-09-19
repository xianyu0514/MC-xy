package dev.xianyu.chronosfm.runtime;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Persistent structural endpoint index.
 *
 * Structural membership is updated only when labels/resource types change.
 * Revisions are mutable primitive state inside Entry so high-frequency endpoint
 * state updates do not allocate replacement EndpointDescriptor records.
 */
public final class PersistentEndpointIndex {
    private final Map<Long, Entry> byId = new HashMap<>();
    private final Map<String, Set<Long>> byLabel = new HashMap<>();
    private final Map<String, Set<Long>> byResource = new HashMap<>();

    public EndpointDelta upsert(EndpointDescriptor endpoint) {
        Objects.requireNonNull(endpoint, "endpoint");
        Entry entry = byId.get(endpoint.endpointId());

        if (entry != null
                && entry.revision == endpoint.revision()
                && entry.labels.equals(endpoint.labels())
                && entry.resourceTypes.equals(endpoint.resourceTypes())) {
            return new EndpointDelta(endpoint, endpoint, false, false);
        }

        EndpointDescriptor previous = entry == null ? null : entry.snapshot();
        boolean structureChanged = entry == null
                || !entry.labels.equals(endpoint.labels())
                || !entry.resourceTypes.equals(endpoint.resourceTypes());

        if (structureChanged && entry != null) removeMembership(entry);

        if (entry == null) {
            entry = new Entry(endpoint);
            byId.put(endpoint.endpointId(), entry);
        } else {
            entry.labels = endpoint.labels();
            entry.resourceTypes = endpoint.resourceTypes();
            entry.revision = endpoint.revision();
        }

        if (structureChanged) addMembership(entry);
        return new EndpointDelta(previous, endpoint, true, structureChanged);
    }

    public EndpointDelta remove(long endpointId) {
        Entry previous = byId.remove(endpointId);
        if (previous == null) return new EndpointDelta(null, null, false, false);
        removeMembership(previous);
        return new EndpointDelta(previous.snapshot(), null, true, true);
    }

    /**
     * Allocation-free revision update used by the per-tick hot path.
     */
    public boolean updateRevision(long endpointId, long nextRevision) {
        Entry entry = byId.get(endpointId);
        if (entry == null) return false;
        if (nextRevision < entry.revision) {
            throw new IllegalArgumentException(
                    "endpoint revision cannot move backwards: "
                            + entry.revision + " -> " + nextRevision
            );
        }
        if (nextRevision == entry.revision) return false;
        entry.revision = nextRevision;
        return true;
    }

    public long revision(long endpointId) {
        Entry entry = byId.get(endpointId);
        return entry == null ? -1 : entry.revision;
    }

    public Optional<EndpointDescriptor> get(long endpointId) {
        Entry entry = byId.get(endpointId);
        return entry == null ? Optional.empty() : Optional.of(entry.snapshot());
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
        private long revision;

        private Entry(EndpointDescriptor descriptor) {
            this.endpointId = descriptor.endpointId();
            this.labels = descriptor.labels();
            this.resourceTypes = descriptor.resourceTypes();
            this.revision = descriptor.revision();
        }

        private EndpointDescriptor snapshot() {
            return new EndpointDescriptor(endpointId, labels, resourceTypes, revision);
        }
    }

    public record EndpointDelta(
            EndpointDescriptor previous,
            EndpointDescriptor current,
            boolean changed,
            boolean structureChanged
    ) {
    }
}
