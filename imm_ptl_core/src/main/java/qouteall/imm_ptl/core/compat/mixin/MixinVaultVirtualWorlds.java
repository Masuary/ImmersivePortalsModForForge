package qouteall.imm_ptl.core.compat.mixin;

import iskallia.vault.core.world.storage.VirtualWorld;
import iskallia.vault.core.world.threading.ThreadingMode;
import iskallia.vault.world.data.VirtualWorlds;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.server.ServerLifecycleHooks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import qouteall.imm_ptl.core.LevelThreads;

import java.util.function.BooleanSupplier;

@Mixin(value = VirtualWorlds.class, remap = false)
public class MixinVaultVirtualWorlds {

    // after the deletions (which unload levels on the server thread) and before the pool tasks are submitted
    @Inject(
        method = "tick",
        at = @At(
            value = "INVOKE",
            target = "Liskallia/vault/world/data/VirtualWorlds;tickDeletions(Lnet/minecraft/server/MinecraftServer;Ljava/util/function/BooleanSupplier;)V",
            shift = At.Shift.AFTER
        )
    )
    private static void ip_markConcurrentlyTickingLevels(BooleanSupplier hasTimeLeft, TickEvent.Phase phase, CallbackInfo ci) {
        // a tick that threw between START and END left its levels marked
        LevelThreads.clearTickingOffServerThread();
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        for (ServerLevel level : server.getAllLevels()) {
            if (level instanceof VirtualWorld world && world.getThreadingMode() == ThreadingMode.CONCURRENT) {
                LevelThreads.markTickingOffServerThread(world);
            }
        }
    }

    @Inject(
        method = "tick",
        at = @At(
            value = "INVOKE",
            target = "Liskallia/vault/core/world/threading/ThreadPool;awaitCompletion()V",
            shift = At.Shift.AFTER
        )
    )
    private static void ip_clearConcurrentlyTickingLevels(BooleanSupplier hasTimeLeft, TickEvent.Phase phase, CallbackInfo ci) {
        LevelThreads.clearTickingOffServerThread();
    }
}
