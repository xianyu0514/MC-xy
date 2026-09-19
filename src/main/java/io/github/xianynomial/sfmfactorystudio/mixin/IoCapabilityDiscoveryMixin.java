package io.github.xianynomial.sfmfactorystudio.mixin;

import ca.teamdman.sfm.common.program.CapabilityConsumer;
import ca.teamdman.sfm.common.program.ProgramContext;
import ca.teamdman.sfm.common.resourcetype.ResourceType;
import ca.teamdman.sfml.ast.InputStatement;
import ca.teamdman.sfml.ast.LabelAccess;
import ca.teamdman.sfml.ast.OutputStatement;
import io.github.xianynomial.sfmfactorystudio.chronosfm.SfmCapabilityRouteCache;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Redirects only SFM Input/Output endpoint discovery.
 *
 * The helper calls ResourceType.forEachCapability itself on every unsupported
 * case, so semantics outside the proven cacheable subset remain upstream SFM.
 */
@Mixin({InputStatement.class, OutputStatement.class})
public abstract class IoCapabilityDiscoveryMixin {
    @Redirect(
            method = "gatherSlots",
            at = @At(
                    value = "INVOKE",
                    target = "Lca/teamdman/sfm/common/resourcetype/ResourceType;forEachCapability(Lca/teamdman/sfm/common/program/ProgramContext;Lca/teamdman/sfml/ast/LabelAccess;Lca/teamdman/sfm/common/program/CapabilityConsumer;)V"
            ),
            require = 0
    )
    @SuppressWarnings({"rawtypes", "unchecked"})
    private void chronosfm$cachedCapabilityRoutes(
            ResourceType resourceType,
            ProgramContext context,
            LabelAccess labelAccess,
            CapabilityConsumer consumer
    ) {
        SfmCapabilityRouteCache.forEachCapability(
                resourceType,
                context,
                labelAccess,
                consumer
        );
    }
}
