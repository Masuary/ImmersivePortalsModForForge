package qouteall.q_misc_util.forge.hooks;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.event.world.WorldEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import qouteall.q_misc_util.MiscHelper;
import qouteall.q_misc_util.dimension.DynamicDimensionsImpl;
import qouteall.q_misc_util.forge.networking.Dim_Sync;
import qouteall.q_misc_util.forge.networking.Message;

public class LevelLoadEvent {

    private static boolean hasStarted = false;

    @SubscribeEvent
    public static void onLevelLoad(WorldEvent.Load dimensionLoadEvent) {
        if (!dimensionLoadEvent.getWorld().isClientSide()) {
            if (hasStarted) {
                Dim_Sync dimSyncPacket = new Dim_Sync();
                for (ServerPlayer player : MiscHelper.getServer().getPlayerList().getPlayers()) {
                    Message.sendToPlayer(dimSyncPacket, player);
                }
            }
        }
    }

    @SubscribeEvent
    public static void onLevelUnload(WorldEvent.Unload dimensionUnloadEvent) {
        if (!dimensionUnloadEvent.getWorld().isClientSide()) {
            if (hasStarted) {
                MinecraftServer server = MiscHelper.getServer();
                ServerLevel unloadedLevel = (ServerLevel) dimensionUnloadEvent.getWorld();
                boolean isExternalRemoval = server.getLevel(unloadedLevel.dimension()) != unloadedLevel;
                if (isExternalRemoval) {
                    DynamicDimensionsImpl.externalDimensionRemovedSignal.emit(unloadedLevel);
                }
                
                Dim_Sync dimSyncPacket = new Dim_Sync();
                for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                    Message.sendToPlayer(dimSyncPacket, player);
                }
            }
        }
    }

    @SubscribeEvent
    public static void serverHasStarted(ServerStartedEvent serverStartedEvent) {
        hasStarted = true;
    }

    @SubscribeEvent
    public static void serverHasStopped(ServerStoppedEvent serverStoppedEvent) {
        hasStarted = false;
    }
}