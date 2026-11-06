package qouteall.imm_ptl.core.compat.vault_hunters;

import iskallia.vault.core.vault.Vault;
import iskallia.vault.world.data.ServerVaults;
import iskallia.vault.world.data.VaultSpectatorData;
import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityTravelToDimensionEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import qouteall.imm_ptl.core.IPGlobal;
import qouteall.imm_ptl.core.compat.mixin.IEVaultServerVaults;
import qouteall.imm_ptl.core.compat.vault_hunters.VaultRules.VaultPresence;
import qouteall.q_misc_util.Helper;
import qouteall.q_misc_util.dimension.DynamicDimensionsImpl;
import qouteall.q_misc_util.my_util.LimitedLogger;

import java.util.List;
import java.util.Optional;
import java.util.WeakHashMap;

/**
 * Vault Hunters rules that need its classes: vault levels are entered and left only through Vault Hunters (V1, vault
 * portals run Vault Hunters' own portal logic, see {@link VaultPortalLinks}),
 * and players without a right to be in a vault level (not a runner, spectator or operator) are sent out of it the
 * way Vault Hunters does it for orphaned vault levels and spectators (V5). Only initialised when the_vault is loaded.
 */
public final class VaultHuntersCompat {
    private static final Logger LOGGER = LogManager.getLogger();
    private static final LimitedLogger rejectedCrossingLogger = new LimitedLogger(100);
    private static final WeakHashMap<ServerPlayer, Integer> ticksWithoutRightToStay = new WeakHashMap<>();

    private VaultHuntersCompat() {
    }

    public static void init() {
        MinecraftForge.EVENT_BUS.addListener(EventPriority.HIGHEST, VaultHuntersCompat::onTravelToDimension);
        MinecraftForge.EVENT_BUS.addListener(VaultHuntersCompat::onPlayerTick);
        DynamicDimensionsImpl.externalDimensionRemovedSignal.connect(VaultHuntersCompat::onLevelRemoved);
        VaultPortalLinks.init();
        VaultPreviews.init();
    }

    public static boolean isNonListenerInVault(ServerPlayer player) {
        Optional<Vault> vault = ServerVaults.get(player.getLevel());
        return vault.isPresent() && !isListener(vault.get(), player);
    }

    private static boolean isListener(Vault vault, ServerPlayer player) {
        return vault.getOptional(Vault.LISTENERS)
            .map(listeners -> listeners.contains(player.getUUID()))
            .orElse(false);
    }

    private static void onTravelToDimension(EntityTravelToDimensionEvent event) {
        if (IPGlobal.serverTeleportationManager == null || !IPGlobal.serverTeleportationManager.isFiringMyTravelEvent) {
            return;
        }
        Entity entity = event.getEntity();
        // a vault portal's crossing is decided right after this event by Vault Hunters' own logic for that portal
        if (entity instanceof ServerPlayer && IPGlobal.serverTeleportationManager.getPortalOfMyTravelEvent() instanceof VaultPortal) {
            return;
        }
        ResourceLocation dimensionFrom = entity.level.dimension().location();
        ResourceLocation dimensionTo = event.getDimension().location();
        if (VaultRules.involvesVaultHuntersDimension(dimensionFrom, dimensionTo)) {
            event.setCanceled(true);
            rejectedCrossingLogger.log("Rejected portal crossing of %s from %s to %s: vault levels are only entered and left through Vault Hunters"
                .formatted(entity, dimensionFrom, dimensionTo));
        }
    }

    private static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer player)) {
            return;
        }
        ResourceLocation dimension = player.level.dimension().location();
        if (!VaultRules.isVaultLevel(dimension)) {
            if (!ticksWithoutRightToStay.isEmpty()) {
                ticksWithoutRightToStay.remove(player);
            }
            return;
        }

        VaultPresence presence = getPresence(player, dimension);
        if (presence != VaultPresence.NON_LISTENER && presence != VaultPresence.VAULT_GONE) {
            ticksWithoutRightToStay.remove(player);
            return;
        }
        int ticks = ticksWithoutRightToStay.merge(player, 1, Integer::sum);
        if (VaultRules.shouldReturnFromVaultLevel(presence, ticks)) {
            ticksWithoutRightToStay.remove(player);
            sendOutOfVaultLevel(player, presence == VaultPresence.VAULT_GONE
                ? "The vault you were in no longer exists"
                : "You are not a runner of this vault");
        }
    }

    private static VaultPresence getPresence(ServerPlayer player, ResourceLocation dimension) {
        Optional<Vault> vault = ServerVaults.get(player.getLevel());
        boolean isTeleporting = player.isChangingDimension()
            || IPGlobal.serverTeleportationManager.isTeleporting(player);
        return VaultRules.classifyPresence(
            dimension,
            vault.isPresent(),
            vault.map(presentVault -> isListener(presentVault, player)).orElse(false),
            player.isSpectator() || player.hasPermissions(2),
            player.isDeadOrDying() || isTeleporting
        );
    }

    // Vault Hunters deletes a finished vault's level without moving the non-listeners still in it
    private static void onLevelRemoved(ServerLevel level) {
        if (!VaultRules.isVaultLevel(level.dimension().location())) {
            return;
        }
        for (ServerPlayer player : List.copyOf(level.players())) {
            if (player.isDeadOrDying()) {
                continue;
            }
            // runs inside Vault Hunters' level deletion: one failing player must not stop the others or IP's cleanup
            try {
                sendOutOfVaultLevel(player, "The vault you were in has closed");
            }
            catch (RuntimeException exception) {
                LOGGER.error("Could not move {} out of the removed level {}",
                    player.getName().getString(), level.dimension().location(), exception);
            }
        }
    }

    // Vault Hunters spectators go back to where they started spectating, everyone else to their spawn point
    private static void sendOutOfVaultLevel(ServerPlayer player, String reason) {
        Helper.log("Sending %s out of %s: %s"
            .formatted(player.getName().getString(), player.level.dimension().location(), reason));
        if (VaultSpectatorData.get(player.server).isPlayerSpectating(player.getUUID())) {
            VaultSpectatorData.stopSpectating(player.getUUID());
        }
        else {
            IEVaultServerVaults.ip_sendToSpawnPoint(player);
        }
        player.sendMessage(
            new TextComponent(reason + ", so you were sent back.").withStyle(ChatFormatting.GOLD),
            Util.NIL_UUID
        );
    }
}
