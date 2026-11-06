package qouteall.imm_ptl.core.compat.mixin;

import iskallia.vault.item.crystal.VaultCrystalItem;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.context.UseOnContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import qouteall.imm_ptl.core.compat.vault_hunters.VaultPreviews;

@Mixin(VaultCrystalItem.class)
public class MixinVaultCrystalItem {

    // Vault Hunters lights the frame on the block in front of the clicked face
    @Inject(method = "useOn", at = @At("RETURN"))
    private void ip_previewLitVault(UseOnContext context, CallbackInfoReturnable<InteractionResult> cir) {
        if (cir.getReturnValue() == InteractionResult.SUCCESS
            && context.getLevel() instanceof ServerLevel hub && context.getPlayer() instanceof ServerPlayer lighter) {
            VaultPreviews.onFrameLit(hub, context.getClickedPos().relative(context.getClickedFace()), lighter);
        }
    }
}
