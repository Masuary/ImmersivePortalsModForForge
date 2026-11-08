package qouteall.imm_ptl.core.compat.vault_hunters;

import java.util.Set;

/**
 * Decides how long a vault created when its hub frame is lit (so the frame shows it before anyone enters) may wait
 * for its first runner. Pure, without Vault Hunters classes, so it is unit tested; {@link VaultPreviews} acts on it.
 */
public final class VaultPreviewRules {
    public static final int TICKS_PER_MINUTE = 20 * 60;

    public enum Mode {
        // Vault Hunters' own behaviour: the vault is made on the first touch
        off,
        untilBroken,
        untilRestart,
        timeout
    }

    private static Mode mode = Mode.untilBroken;
    private static int timeoutMinutes = 5;

    private VaultPreviewRules() {
    }

    public static void configure(Mode configuredMode, int configuredTimeoutMinutes) {
        if (configuredTimeoutMinutes < 1) {
            throw new IllegalArgumentException("vaultPreviewTimeoutMinutes must be at least 1, was " + configuredTimeoutMinutes);
        }
        mode = configuredMode;
        timeoutMinutes = configuredTimeoutMinutes;
    }

    public static Mode getMode() {
        return mode;
    }

    // Wold's Vaults objectives whose logic runs as soon as the vault exists, before any runner is inside (Corrupted
    // sets its global corruption state, Hyper rolls objectives and counts its event timer)
    private static final Set<String> OBJECTIVES_STARTING_WITHOUT_RUNNERS = Set.of("corrupted", "hyper");

    /**
     * Whether lighting this frame creates its vault at once. Floor frames and plain frames get no window, Greed
     * challenge crystals would fail their challenge if an unentered vault were dropped, and some objectives start
     * without runners, so all of them keep Vault Hunters' first-touch creation.
     */
    public static boolean shouldPreview(
        Mode currentMode, boolean isFloorFrame, boolean isPlainFrame, boolean isChallengeCrystal, boolean isVaultTurnedOff,
        String objectiveType
    ) {
        return currentMode != Mode.off && !isFloorFrame && !isPlainFrame && !isChallengeCrystal && !isVaultTurnedOff
            && !OBJECTIVES_STARTING_WITHOUT_RUNNERS.contains(objectiveType);
    }

    public static boolean isExpired(Mode currentMode, int currentTimeoutMinutes, long createdAtGameTime, long nowGameTime) {
        return currentMode == Mode.timeout && nowGameTime - createdAtGameTime >= (long) currentTimeoutMinutes * TICKS_PER_MINUTE;
    }

    /**
     * Previews left from the previous run are dropped at startup in {@code untilRestart}; {@code off} also drops them,
     * so turning the feature off leaves no vault waiting forever.
     */
    public static boolean isDroppedAtStartup(Mode currentMode) {
        return currentMode == Mode.untilRestart || currentMode == Mode.off;
    }

    public static boolean isExpiredNow(long createdAtGameTime, long nowGameTime) {
        return isExpired(mode, timeoutMinutes, createdAtGameTime, nowGameTime);
    }
}
