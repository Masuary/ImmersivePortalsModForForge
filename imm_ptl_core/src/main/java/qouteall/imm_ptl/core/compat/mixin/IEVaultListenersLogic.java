package qouteall.imm_ptl.core.compat.mixin;

import iskallia.vault.core.vault.Vault;
import iskallia.vault.core.vault.player.ListenersLogic;
import iskallia.vault.core.world.storage.VirtualWorld;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(value = ListenersLogic.class, remap = false)
public interface IEVaultListenersLogic {
    @Invoker("onTeleport")
    void ip_onTeleport(VirtualWorld world, Vault vault, ServerPlayer player);
}
