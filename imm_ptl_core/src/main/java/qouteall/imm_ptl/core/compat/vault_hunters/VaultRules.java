package qouteall.imm_ptl.core.compat.vault_hunters;

import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.fml.ModList;

/**
 * Vault Hunters rules for portal crossings and cross-portal block actions. Decided on dimension ids only, so this
 * class never loads Vault Hunters classes and also runs on the client and without Vault Hunters.
 */
public final class VaultRules {
    public static final String VAULT_HUNTERS_MOD_ID = "the_vault";
    // Vault Hunters caps the reach attribute at this value inside vaults
    public static final double VAULT_REACH_CAP = 7.0;
    // the padding Forge's own server-side reach checks (IForgePlayer.canInteractWith) add for breaking and using
    public static final double BREAK_REACH_PADDING = 1.0;
    public static final double USE_REACH_PADDING = 3.0;
    // Vault Hunters' own flows (exit recall, spectating) leave a player briefly in a vault level as a non-listener
    public static final int NON_LISTENER_GRACE_TICKS = 40;
    // a runner who came in through a window arrives right at the start-room window; a step back must not bail them
    public static final int EXIT_AFTER_ENTRY_DELAY_TICKS = 60;
    // the server sees a crossing player up to a tick of movement past a window before the crossing itself arrives;
    // Vault Hunters' own touch takes over only from a player who stays behind the window this long without crossing
    public static final int WINDOW_FALLBACK_DELAY_TICKS = 20;

    public enum VaultPresence {
        OUTSIDE_VAULT_LEVEL,
        LISTENER,
        EXEMPT,
        NON_LISTENER,
        VAULT_GONE
    }

    // ENTRY: hub frame into the vault's start room, EXIT: start-room portal back to the hub frame
    public enum VaultPortalRole {
        ENTRY,
        EXIT
    }

    private VaultRules() {
    }

    public static boolean isVaultHuntersPresent() {
        return ModList.get().isLoaded(VAULT_HUNTERS_MOD_ID);
    }

    public static boolean isVaultHuntersDimension(ResourceLocation dimension) {
        return dimension.getNamespace().equals(VAULT_HUNTERS_MOD_ID);
    }

    public static boolean isVaultLevel(ResourceLocation dimension) {
        return isVaultHuntersDimension(dimension) && dimension.getPath().startsWith("vault_");
    }

    /**
     * A portal crossing or a cross-portal block action between these dimensions bypasses Vault Hunters' join, exit
     * and per-runner rules when either side is a Vault Hunters dimension.
     */
    public static boolean involvesVaultHuntersDimension(ResourceLocation playerDimension, ResourceLocation otherDimension) {
        return isVaultHuntersDimension(playerDimension) || isVaultHuntersDimension(otherDimension);
    }

    /**
     * The client predicts a crossing only where the server can accept it: never into or out of a Vault Hunters
     * dimension, except through a vault portal, whose crossings Vault Hunters itself approves on the server.
     */
    public static boolean mayPredictCrossing(
        ResourceLocation playerDimension, ResourceLocation portalDestination, boolean isVaultPortal
    ) {
        return isVaultPortal || !involvesVaultHuntersDimension(playerDimension, portalDestination);
    }

    /**
     * Only players walk through vault portals (Vault Hunters' own frame does the same); spectators never, as they
     * would bypass the join and exit flows.
     */
    public static boolean mayUseVaultPortal(boolean isPlayer, boolean isSpectator) {
        return isPlayer && !isSpectator;
    }

    /**
     * After Vault Hunters' own portal logic ran for the crossing player: an entry is admitted only when it made the
     * player a new runner (a runner who is still outside is pulled in by Vault Hunters itself), an exit only when it
     * removed the player from the vault (bail and exit objectives decide).
     */
    public static boolean isVaultPortalCrossingAdmitted(
        VaultPortalRole role, boolean wasListenerBefore, boolean isListenerAfter
    ) {
        return switch (role) {
            case ENTRY -> !wasListenerBefore && isListenerAfter;
            case EXIT -> wasListenerBefore && !isListenerAfter;
        };
    }

    /**
     * Whether Vault Hunters' own touch of its portal block is left to a window's crossing: while the player is in front
     * of a window, shortly after (the crossing is on its way), and in the vault right after a window entry.
     */
    public static boolean isTouchLeftToWindow(
        boolean isInFrontOfWindow, long ticksSinceInFrontOfWindow, boolean isInVault, long ticksSinceWindowEntry
    ) {
        return isInFrontOfWindow
            || ticksSinceInFrontOfWindow < WINDOW_FALLBACK_DELAY_TICKS
            || (isInVault && isExitTooSoonAfterEntry(ticksSinceWindowEntry));
    }

    public static boolean isExitTooSoonAfterEntry(long ticksSinceWindowEntry) {
        return ticksSinceWindowEntry >= 0 && ticksSinceWindowEntry < EXIT_AFTER_ENTRY_DELAY_TICKS;
    }

    public static double capRemoteReach(double playerReach) {
        return Math.min(playerReach, VAULT_REACH_CAP);
    }

    public static VaultPresence classifyPresence(
        ResourceLocation playerDimension,
        boolean vaultExists,
        boolean isListener,
        boolean mayWatchWithoutJoining,
        boolean isDeadOrChangingDimension
    ) {
        if (!isVaultLevel(playerDimension)) {
            return VaultPresence.OUTSIDE_VAULT_LEVEL;
        }
        if (isDeadOrChangingDimension) {
            return VaultPresence.EXEMPT;
        }
        if (!vaultExists) {
            return VaultPresence.VAULT_GONE;
        }
        if (isListener) {
            return VaultPresence.LISTENER;
        }
        if (mayWatchWithoutJoining) {
            return VaultPresence.EXEMPT;
        }
        return VaultPresence.NON_LISTENER;
    }

    public static boolean shouldReturnFromVaultLevel(VaultPresence presence, int ticksWithoutRight) {
        boolean hasNoRightToStay = presence == VaultPresence.NON_LISTENER || presence == VaultPresence.VAULT_GONE;
        return hasNoRightToStay && ticksWithoutRight >= NON_LISTENER_GRACE_TICKS;
    }
}
