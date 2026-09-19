package dev.xianyu.chronosfm.runtime;

import java.util.Objects;
import java.util.Set;

public record EndpointDescriptor(
        long endpointId,
        Set<String> labels,
        Set<String> resourceTypes,
        long revision
) {
    public EndpointDescriptor {
        if (endpointId < 0) throw new IllegalArgumentException("endpointId must be >= 0");
        labels = Set.copyOf(Objects.requireNonNull(labels, "labels"));
        resourceTypes = Set.copyOf(Objects.requireNonNull(resourceTypes, "resourceTypes"));
        if (revision < 0) throw new IllegalArgumentException("revision must be >= 0");
    }

    public EndpointDescriptor withRevision(long nextRevision) {
        return new EndpointDescriptor(endpointId, labels, resourceTypes, nextRevision);
    }

    public boolean hasSameStructure(EndpointDescriptor other) {
        return other != null
                && labels.equals(other.labels)
                && resourceTypes.equals(other.resourceTypes);
    }
}
