package io.github.xianynomial.sfmfactorystudio.chronosfm;

import ca.teamdman.sfm.common.block_network.CableNetwork;
import ca.teamdman.sfm.common.block_network.SFMBlockCapabilityCacheForLevel;
import ca.teamdman.sfm.common.label.LabelPositionHolder;
import ca.teamdman.sfm.common.program.ProgramContext;

import java.util.Optional;

/**
 * Identity + monotonic revision stamp for SFM endpoint-discovery structure.
 *
 * This is deliberately fail-closed: if any target class was not transformed by
 * the expected revision mixin, capture() returns empty and endpoint caching must
 * stay disabled.
 */
public final class SfmStructureStamp {
    private final LabelPositionHolder labels;
    private final CableNetwork network;
    private final SFMBlockCapabilityCacheForLevel capabilityCache;

    private final long labelRevision;
    private final long networkRevision;
    private final long capabilityRevision;

    private SfmStructureStamp(
            LabelPositionHolder labels,
            CableNetwork network,
            SFMBlockCapabilityCacheForLevel capabilityCache,
            long labelRevision,
            long networkRevision,
            long capabilityRevision
    ) {
        this.labels = labels;
        this.network = network;
        this.capabilityCache = capabilityCache;
        this.labelRevision = labelRevision;
        this.networkRevision = networkRevision;
        this.capabilityRevision = capabilityRevision;
    }

    public static Optional<SfmStructureStamp> capture(ProgramContext context) {
        if (context == null) return Optional.empty();

        LabelPositionHolder labels = context.getLabelPositionHolder();
        CableNetwork network = context.getNetwork();
        if (labels == null || network == null) return Optional.empty();

        SFMBlockCapabilityCacheForLevel capabilityCache = network.getLevelCapabilityCache();

        ChronoRevisionSource labelSource = asRevisionSource(labels);
        ChronoRevisionSource networkSource = asRevisionSource(network);
        ChronoRevisionSource capabilitySource = asRevisionSource(capabilityCache);
        if (labelSource == null || networkSource == null || capabilitySource == null) {
            return Optional.empty();
        }

        return Optional.of(new SfmStructureStamp(
                labels,
                network,
                capabilityCache,
                labelSource.chronosfm$getRevision(),
                networkSource.chronosfm$getRevision(),
                capabilitySource.chronosfm$getRevision()
        ));
    }

    public boolean isStillValid(ProgramContext context) {
        if (context == null) return false;
        if (context.getLabelPositionHolder() != labels) return false;
        if (context.getNetwork() != network) return false;
        if (network.getLevelCapabilityCache() != capabilityCache) return false;

        ChronoRevisionSource labelSource = asRevisionSource(labels);
        ChronoRevisionSource networkSource = asRevisionSource(network);
        ChronoRevisionSource capabilitySource = asRevisionSource(capabilityCache);
        if (labelSource == null || networkSource == null || capabilitySource == null) {
            return false;
        }

        return labelSource.chronosfm$getRevision() == labelRevision
                && networkSource.chronosfm$getRevision() == networkRevision
                && capabilitySource.chronosfm$getRevision() == capabilityRevision;
    }

    private static ChronoRevisionSource asRevisionSource(Object value) {
        return value instanceof ChronoRevisionSource source ? source : null;
    }

    public long labelRevision() {
        return labelRevision;
    }

    public long networkRevision() {
        return networkRevision;
    }

    public long capabilityRevision() {
        return capabilityRevision;
    }
}
