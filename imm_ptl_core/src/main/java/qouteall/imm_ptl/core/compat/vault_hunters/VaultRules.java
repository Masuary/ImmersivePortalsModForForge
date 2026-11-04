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

    public enum VaultPresence {
        OUTSIDE_VAULT_LEVEL,
        LISTENER,
        EXEMPT,
        NON_LISTENER,
        VAULT_GONE
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
