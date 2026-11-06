package qouteall.imm_ptl.core.compat.vault_hunters;

import org.junit.jupiter.api.Test;
import qouteall.imm_ptl.core.compat.vault_hunters.VaultPreviewRules.Mode;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VaultPreviewRulesTest {

    @Test
    void aLitWallFramePreviewsItsVaultUnlessTurnedOff() {
        assertTrue(VaultPreviewRules.shouldPreview(Mode.untilBroken, false, false, false));
        assertTrue(VaultPreviewRules.shouldPreview(Mode.timeout, false, false, false));
        assertFalse(VaultPreviewRules.shouldPreview(Mode.off, false, false, false));
    }

    @Test
    void floorFramesChallengeCrystalsAndDisabledVaultsKeepTheFirstTouch() {
        assertFalse(VaultPreviewRules.shouldPreview(Mode.untilBroken, true, false, false));
        assertFalse(VaultPreviewRules.shouldPreview(Mode.untilBroken, false, true, false));
        assertFalse(VaultPreviewRules.shouldPreview(Mode.untilBroken, false, false, true));
    }

    @Test
    void onlyTheTimeoutModeExpiresAfterItsMinutes() {
        long created = 1000;
        long fiveMinutesLater = created + 5L * VaultPreviewRules.TICKS_PER_MINUTE;
        assertFalse(VaultPreviewRules.isExpired(Mode.timeout, 5, created, fiveMinutesLater - 1));
        assertTrue(VaultPreviewRules.isExpired(Mode.timeout, 5, created, fiveMinutesLater));
        assertFalse(VaultPreviewRules.isExpired(Mode.untilBroken, 5, created, fiveMinutesLater * 100));
        assertFalse(VaultPreviewRules.isExpired(Mode.untilRestart, 5, created, fiveMinutesLater * 100));
    }

    @Test
    void untilRestartAndOffDropLeftoverPreviewsAtStartup() {
        assertTrue(VaultPreviewRules.isDroppedAtStartup(Mode.untilRestart));
        assertTrue(VaultPreviewRules.isDroppedAtStartup(Mode.off));
        assertFalse(VaultPreviewRules.isDroppedAtStartup(Mode.untilBroken));
        assertFalse(VaultPreviewRules.isDroppedAtStartup(Mode.timeout));
    }

    @Test
    void aTimeoutBelowOneMinuteIsRefused() {
        assertThrows(IllegalArgumentException.class, () -> VaultPreviewRules.configure(Mode.timeout, 0));
    }
}
