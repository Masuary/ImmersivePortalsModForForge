package qouteall.imm_ptl.core.compat.mixin;

import iskallia.vault.core.vault.Vault;
import iskallia.vault.core.vault.objective.Objectives;
import iskallia.vault.core.world.storage.VirtualWorld;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import qouteall.imm_ptl.core.compat.vault_hunters.VaultPreviews;

@Mixin(value = Objectives.class, remap = false)
public class MixinVaultObjectives {

    @Inject(method = "tickServer", at = @At("HEAD"), cancellable = true)
    private void ip_holdPreviewObjectives(VirtualWorld world, Vault vault, CallbackInfo ci) {
        if (VaultPreviews.holdObjectivesBeforeTick(vault)) {
            ci.cancel();
        }
    }
}
