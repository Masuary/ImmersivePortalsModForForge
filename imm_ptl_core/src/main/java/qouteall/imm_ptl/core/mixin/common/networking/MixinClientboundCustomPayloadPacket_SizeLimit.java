package qouteall.imm_ptl.core.mixin.common.networking;

import net.minecraft.network.protocol.game.ClientboundCustomPayloadPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

// Below default priority and optional: when another mod changes the same constant and requires its change
// (Vault Hunters raises it to Integer.MAX_VALUE), Mixin keeps the higher-priority one instead of failing.
@Mixin(value = ClientboundCustomPayloadPacket.class, priority = 900)
public class MixinClientboundCustomPayloadPacket_SizeLimit {

    @ModifyConstant(
        method = "<init>(Lnet/minecraft/network/FriendlyByteBuf;)V",
        constant = @Constant(intValue = 1048576),
        require = 0
    )
    private int modifySizeLimitWhenReadingPacket(int oldValue) {
        return 233333333;
    }
}
