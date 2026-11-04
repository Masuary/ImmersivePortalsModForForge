package qouteall.imm_ptl.core.compat.mixin;

import iskallia.vault.world.data.ServerVaults;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(value = ServerVaults.class, remap = false)
public interface IEVaultServerVaults {
    @Invoker("sendToSpawnPoint")
    static void ip_sendToSpawnPoint(ServerPlayer player) {
        throw new AssertionError("replaced by the mixin invoker");
    }
}
