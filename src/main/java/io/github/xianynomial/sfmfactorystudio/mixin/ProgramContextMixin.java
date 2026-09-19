package io.github.xianynomial.sfmfactorystudio.mixin;

import ca.teamdman.sfm.common.block_network.BlockNetworkManager;
import ca.teamdman.sfm.common.block_network.CableNetwork;
import ca.teamdman.sfm.common.block_network.CableNetworkManager;
import ca.teamdman.sfm.common.blockentity.ManagerBlockEntity;
import ca.teamdman.sfm.common.program.ProgramContext;
import ca.teamdman.sfm.common.util.Unit;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.Optional;

/**
 * Read-mostly ProgramContext network lookup.
 *
 * The released SFM path calls getOrRegisterNetworkFromManagerPosition for every
 * ProgramContext construction. SFM already owns a persistent position -> network
 * index, so a due program tick first performs a side-effect-free read and only
 * falls back to the original registration/repair path on a real miss.
 *
 * No second network cache is retained here.
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
            BlockNetworkManager<Level, Unit, CableNetwork> networkManager =
                    CableNetworkManagerAccessor.chronosfm$getNetworkManager();
            CableNetwork existing = networkManager.getNetwork(level, manager.getBlockPos());
            if (existing != null) {
                return Optional.of(existing);
            }
        }

        // Preserve SFM's original lazy construction/repair behavior on a miss.
        return CableNetworkManager.getOrRegisterNetworkFromManagerPosition(manager);
    }
}
