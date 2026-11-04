package qouteall.imm_ptl.core.compat.mixin;

import iskallia.vault.entity.entity.SpiritEntity;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import qouteall.imm_ptl.core.compat.vault_hunters.VaultHuntersCompat;
import qouteall.q_misc_util.Helper;

@Mixin(value = SpiritEntity.class, remap = false)
public class MixinVaultSpiritEntity {
    
    // the spirit is built from the dead player's listener entry, which a non-listener does not have
    @Inject(method = "onPlayerDeath", at = @At("HEAD"), cancellable = true)
    private static void ip_skipNonListeners(LivingDeathEvent event, CallbackInfo ci) {
        if (event.getEntity() instanceof ServerPlayer player && VaultHuntersCompat.isNonListenerInVault(player)) {
            Helper.log("Skipped the Vault Hunters spirit of %s, who is not a runner of %s"
                .formatted(player.getName().getString(), player.level.dimension().location()));
            ci.cancel();
        }
    }
}
