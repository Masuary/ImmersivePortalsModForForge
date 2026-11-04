package qouteall.imm_ptl.core.compat.mixin;

import iskallia.vault.core.world.storage.VirtualWorld;
import iskallia.vault.core.world.threading.ThreadingMode;
import iskallia.vault.world.data.VirtualWorlds;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(value = VirtualWorlds.class, remap = false)
public class MixinVaultVirtualWorlds {

    // Cross-portal collision and chunk loading read other levels' chunks. With vault levels ticking on Vault Hunters'
    // thread pool next to the main thread, such a read waits for a thread that is itself waiting: tick them on the
    // server thread instead. Only the tick dispatch changes; the saved threading mode stays as Vault Hunters set it.
    @Redirect(
        method = "tick",
        at = @At(
            value = "INVOKE",
            target = "Liskallia/vault/core/world/storage/VirtualWorld;getThreadingMode()Liskallia/vault/core/world/threading/ThreadingMode;"
        )
    )
    private static ThreadingMode ip_tickOnServerThread(VirtualWorld world) {
        return ThreadingMode.SEQUENTIAL;
    }
}
