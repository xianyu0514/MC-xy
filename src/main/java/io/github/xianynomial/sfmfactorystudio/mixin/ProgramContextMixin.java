package io.github.xianynomial.sfmfactorystudio.mixin;

import ca.teamdman.sfm.common.block_network.CableNetwork;
import ca.teamdman.sfm.common.block_network.CableNetworkManager;
import ca.teamdman.sfm.common.blockentity.ManagerBlockEntity;
import ca.teamdman.sfm.common.program.ProgramContext;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.Optional;

/**
 * Read-mostly ProgramContext network lookup.
 *
 * SFM's normal path calls getOrRegisterNetworkFromManagerPosition for every
 * ProgramContext construction. The network manager itself already keeps a
 * persistent position -> network index, so a due program tick can first perform
 * the side-effect-free lookup and only fall back to the original registration
 * path on a real miss.
 *
 * This changes no transfer/trigger semantics and does not retain a second
 * network cache.
 */
@Mixin(value = ProgramContext.class)
public abstract class ProgramContextMixin {
    @Redirect(
            method = "<init>(Lca/teamdman/sfml/ast/Program;Lca/teamdman/sfm/common/blockentity/ManagerBlockEntity;Lca/teamdman/sfm/common/program/ProgramBehaviour;)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lca/teamdman/sfm/common/block_network/CableNetworkManager;getOrRegisterNetworkFromManagerPosition(Lca/teamdman/sfm/common/blockentity/ManagerBlockEntity;)Ljava/util/Optional;"
            ),
            require = 0
    )
    private Optional<CableNetwork> chronosfm$readExistingNetworkFirst(ManagerBlockEntity manager) {
        Level level = manager.getLevel();
        if (level != null) {
            Optional<CableNetwork> existing = CableNetworkManager.getNetworkFromCablePosition(
                    level,
                    manager.getBlockPos()
            );
            if (existing.isPresent()) return existing;
        }

        // Preserve SFM's original lazy construction/repair behavior on a miss.
        return CableNetworkManager.getOrRegisterNetworkFromManagerPosition(manager);
    }
}
