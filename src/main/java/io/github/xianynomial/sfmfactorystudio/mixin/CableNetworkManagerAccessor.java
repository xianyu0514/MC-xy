package io.github.xianynomial.sfmfactorystudio.mixin;

import ca.teamdman.sfm.common.block_network.BlockNetworkManager;
import ca.teamdman.sfm.common.block_network.CableNetwork;
import ca.teamdman.sfm.common.block_network.CableNetworkManager;
import ca.teamdman.sfm.common.util.Unit;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Release-compatible read-only access to SFM's already-persistent network
 * index. SFM 1.21.1 HEAD exposes a public getNetworkFromCablePosition helper,
 * but released 4.34.0 does not. Reading the existing manager directly preserves
 * the same optimization without depending on that newer public helper.
 */
@Mixin(CableNetworkManager.class)
public interface CableNetworkManagerAccessor {
    @Accessor("NETWORK_MANAGER")
    static BlockNetworkManager<Level, Unit, CableNetwork> chronosfm$getNetworkManager() {
        throw new AssertionError("mixin accessor was not transformed");
    }
}
