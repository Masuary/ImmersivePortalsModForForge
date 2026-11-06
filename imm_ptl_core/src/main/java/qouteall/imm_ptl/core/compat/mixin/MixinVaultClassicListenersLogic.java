package qouteall.imm_ptl.core.compat.mixin;

import iskallia.vault.core.vault.Vault;
import iskallia.vault.core.vault.player.ClassicListenersLogic;
import iskallia.vault.core.vault.player.Listener;
import iskallia.vault.core.world.storage.VirtualWorld;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import qouteall.imm_ptl.core.compat.vault_hunters.VaultPreviews;

import java.util.Map;
import java.util.UUID;

@Mixin(value = ClassicListenersLogic.class, remap = false)
public class MixinVaultClassicListenersLogic {

    // without listeners the tick ends and deletes the vault; a preview waits for its first runner instead
    @Inject(method = "tickServer", at = @At("HEAD"), cancellable = true)
    private void ip_keepPreviewWaiting(VirtualWorld world, Vault vault, Map<UUID, Listener> listeners, CallbackInfo ci) {
        if (VaultPreviews.isWaitingForRunners(vault)) {
            ci.cancel();
        }
    }
}
