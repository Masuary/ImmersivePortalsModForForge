package qouteall.imm_ptl.core.compat.mixin;

import iskallia.vault.core.world.generator.BatchBlockPlacer;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import qouteall.imm_ptl.core.IPGlobal;
import qouteall.q_misc_util.my_util.MyTaskList;

import java.util.List;

@Mixin(value = BatchBlockPlacer.class, remap = false)
public class MixinVaultBatchBlockPlacer {

    // Vault Hunters places template rooms without block updates and resends the chunks 500 ms (10 ticks) later,
    // but only to the players inside the vault; players watching through a portal need the same resend.
    @Inject(method = "scheduleChunkResend", at = @At("HEAD"))
    private static void ip_resendToRemoteWatchers(
        ServerLevel level, List<ServerPlayer> players, ChunkPos chunkPos, CallbackInfo ci
    ) {
        MinecraftServer server = level.getServer();
        IPGlobal.serverTaskList.addTask(MyTaskList.withDelay(11, MyTaskList.oneShotTask(() -> {
            if (server.getLevel(level.dimension()) == level) {
                IPGlobal.chunkDataSyncManager.resendChunkToRemoteWatchers(level, chunkPos);
            }
        })));
    }
}
