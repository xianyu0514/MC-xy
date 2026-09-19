package dev.xianyu.chronosfm.ir;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class DependencyIndex {
    private final Map<String, int[]> byLabel;
    private final Map<String, int[]> byResource;
    private final Map<LabelResourceKey, int[]> byLabelAndResource;
    private final Map<String, int[]> byLabelAnyResource;

    private DependencyIndex(
            Map<String, int[]> byLabel,
            Map<String, int[]> byResource,
            Map<LabelResourceKey, int[]> byLabelAndResource,
            Map<String, int[]> byLabelAnyResource
    ) {
        this.byLabel = Map.copyOf(byLabel);
        this.byResource = Map.copyOf(byResource);
        this.byLabelAndResource = Map.copyOf(byLabelAndResource);
        this.byLabelAnyResource = Map.copyOf(byLabelAnyResource);
    }

    public static DependencyIndex build(List<TransferRegion> regions) {
        return build(regions, List.of());
    }

    public static DependencyIndex build(
            List<TransferRegion> regions,
            List<ExactOperation> operations
    ) {
        Map<String, BitSet> labelBits = new HashMap<>();
        Map<String, BitSet> resourceBits = new HashMap<>();
        Map<LabelResourceKey, BitSet> pairBits = new HashMap<>();
        Map<String, BitSet> labelAnyResourceBits = new HashMap<>();

        for (TransferRegion region : regions) {
            int id = region.regionId();
            addLabelResource(labelBits, resourceBits, pairBits, region.sourceLabel(), region.resourceKey(), id);
            addLabelResource(labelBits, resourceBits, pairBits, region.destinationLabel(), region.resourceKey(), id);
        }

        for (ExactOperation operation : operations) {
            int id = operation.regionId();
            List<String> resources = operation.resourceTypes();
            for (String label : operation.labels()) {
                add(labelBits, label, id);
                if (resources.isEmpty()) {
                    add(labelAnyResourceBits, label, id);
                } else {
                    for (String resource : resources) {
                        add(resourceBits, resource, id);
                        add(pairBits, new LabelResourceKey(label, resource), id);
                    }
                }
            }
        }

        return new DependencyIndex(
                freeze(labelBits),
                freeze(resourceBits),
                freeze(pairBits),
                freeze(labelAnyResourceBits)
        );
    }

    public int[] regionsForLabel(String label) {
        return cloneOrEmpty(byLabel.get(label));
    }

    public int[] regionsForResource(String resourceKey) {
        return cloneOrEmpty(byResource.get(resourceKey));
    }

    /**
     * Returns only work regions that can observe the supplied endpoint.
     *
     * When resource types are known, label/resource composite dependencies keep
     * a state change on one endpoint from dirtying every region of the same
     * resource type. Empty resourceTypes means "unknown", so the label index is
     * used conservatively.
     */
    public int[] regionsForEndpoint(
            Collection<String> labels,
            Collection<String> resourceTypes
    ) {
        Objects.requireNonNull(labels, "labels");
        Objects.requireNonNull(resourceTypes, "resourceTypes");

        BitSet result = new BitSet();
        if (resourceTypes.isEmpty()) {
            for (String label : labels) mark(result, byLabel.get(label));
            return result.stream().toArray();
        }

        for (String label : labels) {
            mark(result, byLabelAnyResource.get(label));
            for (String resource : resourceTypes) {
                mark(result, byLabelAndResource.get(new LabelResourceKey(label, resource)));
            }
        }
        return result.stream().toArray();
    }

    private static void addLabelResource(
            Map<String, BitSet> labelBits,
            Map<String, BitSet> resourceBits,
            Map<LabelResourceKey, BitSet> pairBits,
            String label,
            String resource,
            int id
    ) {
        add(labelBits, label, id);
        add(resourceBits, resource, id);
        add(pairBits, new LabelResourceKey(label, resource), id);
    }

    private static <K> void add(Map<K, BitSet> map, K key, int regionId) {
        map.computeIfAbsent(key, ignored -> new BitSet()).set(regionId);
    }

    private static void mark(BitSet target, int[] ids) {
        if (ids == null) return;
        for (int id : ids) target.set(id);
    }

    private static int[] cloneOrEmpty(int[] value) {
        return value == null ? new int[0] : value.clone();
    }

    private static <K> Map<K, int[]> freeze(Map<K, BitSet> source) {
        Map<K, int[]> result = new HashMap<>();
        source.forEach((key, bits) -> result.put(key, bits.stream().toArray()));
        return result;
    }

    private record LabelResourceKey(String label, String resource) {
        private LabelResourceKey {
            Objects.requireNonNull(label, "label");
            Objects.requireNonNull(resource, "resource");
        }
    }
}
