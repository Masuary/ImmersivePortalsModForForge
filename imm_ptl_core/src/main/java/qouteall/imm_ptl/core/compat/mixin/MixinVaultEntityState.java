package qouteall.imm_ptl.core.compat.mixin;

import iskallia.vault.core.vault.EntityState;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import qouteall.imm_ptl.core.compat.vault_hunters.VaultPortalLinks;

@Mixin(value = EntityState.class, remap = false)
public class MixinVaultEntityState {

    // an exit through the start-room window is its own way back to the frame
    @Inject(method = "teleport", at = @At("HEAD"), cancellable = true)
    private void ip_keepWindowExitInPlace(Entity entity, CallbackInfo ci) {
        if (VaultPortalLinks.replaceRecallTeleport((EntityState) (Object) this, entity)) {
            ci.cancel();
        }
    }
}
