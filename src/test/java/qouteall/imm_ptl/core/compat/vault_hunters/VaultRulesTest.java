package qouteall.imm_ptl.core.compat.vault_hunters;

import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import qouteall.imm_ptl.core.compat.vault_hunters.VaultRules.VaultPortalRole;
import qouteall.imm_ptl.core.compat.vault_hunters.VaultRules.VaultPresence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

// Catalogue numbers refer to section 4 of the research report 03-vh-portals-interaction.md
class VaultRulesTest {
    private static final ResourceLocation OVERWORLD = new ResourceLocation("minecraft", "overworld");
    private static final ResourceLocation NETHER = new ResourceLocation("minecraft", "the_nether");
    private static final ResourceLocation VAULT = new ResourceLocation("the_vault", "vault_3f2a1c4e-0000-4000-8000-000000000001");
    private static final ResourceLocation OTHER_VAULT = new ResourceLocation("the_vault", "vault_3f2a1c4e-0000-4000-8000-000000000002");
    private static final ResourceLocation ARENA = new ResourceLocation("the_vault", "arena");

    @Test
    void vaultLevelsAreVaultPrefixedDimensionsOfTheVaultNamespace() {
        assertTrue(VaultRules.isVaultLevel(VAULT));
        assertFalse(VaultRules.isVaultLevel(ARENA));
        assertTrue(VaultRules.isVaultHuntersDimension(ARENA));
        assertFalse(VaultRules.isVaultHuntersDimension(OVERWORLD));
        assertFalse(VaultRules.isVaultLevel(new ResourceLocation("othermod", "vault_1")));
    }

    // 4.9 items, 4.10 pearls, 4.16 spectators, 4.17 walk-ins: nothing crosses from the hub into a vault
    @Test
    void crossingIntoAVaultIsRejected() {
        assertTrue(VaultRules.involvesVaultHuntersDimension(OVERWORLD, VAULT));
        assertTrue(VaultRules.involvesVaultHuntersDimension(OVERWORLD, ARENA));
    }

    // 4.5-4.8 projectiles, mobs and loot, 4.16 spectators, 4.18 runners: nothing crosses out of a vault
    @Test
    void crossingOutOfAVaultIsRejected() {
        assertTrue(VaultRules.involvesVaultHuntersDimension(VAULT, OVERWORLD));
        assertTrue(VaultRules.involvesVaultHuntersDimension(VAULT, OTHER_VAULT));
    }

    @Test
    void crossingsBetweenOtherDimensionsAreLeftAlone() {
        assertFalse(VaultRules.involvesVaultHuntersDimension(OVERWORLD, NETHER));
        assertFalse(VaultRules.involvesVaultHuntersDimension(OVERWORLD, OVERWORLD));
    }

    // 4.1-4.4 outsiders breaking, placing, opening and using vault blocks; 4.12 runners using hub blocks
    @Test
    void remoteBlockActionsTouchingAVaultAreRejected() {
        assertTrue(VaultRules.involvesVaultHuntersDimension(OVERWORLD, VAULT));
        assertTrue(VaultRules.involvesVaultHuntersDimension(VAULT, OVERWORLD));
        assertTrue(VaultRules.involvesVaultHuntersDimension(VAULT, VAULT));
    }

    // 4.19 IP's own cross-portal reach was 24 blocks
    @Test
    void remoteReachNeverExceedsTheVaultCap() {
        assertEquals(4.5, VaultRules.capRemoteReach(4.5));
        assertEquals(VaultRules.VAULT_REACH_CAP, VaultRules.capRemoteReach(7.0));
        assertEquals(VaultRules.VAULT_REACH_CAP, VaultRules.capRemoteReach(24.0));
    }

    @Test
    void listenersStayInTheirVault() {
        VaultPresence presence = VaultRules.classifyPresence(VAULT, true, true, false, false);
        assertEquals(VaultPresence.LISTENER, presence);
        assertFalse(VaultRules.shouldReturnFromVaultLevel(presence, 1000));
    }

    // 4.15, 4.17 a non-listener in a running vault is returned once the grace period is over
    @Test
    void nonListenersAreReturnedAfterTheGracePeriod() {
        VaultPresence presence = VaultRules.classifyPresence(VAULT, true, false, false, false);
        assertEquals(VaultPresence.NON_LISTENER, presence);
        assertFalse(VaultRules.shouldReturnFromVaultLevel(presence, VaultRules.NON_LISTENER_GRACE_TICKS - 1));
        assertTrue(VaultRules.shouldReturnFromVaultLevel(presence, VaultRules.NON_LISTENER_GRACE_TICKS));
    }

    // spectators and operators watching a running vault
    @Test
    void watchersOfARunningVaultMayStay() {
        VaultPresence presence = VaultRules.classifyPresence(VAULT, true, false, true, false);
        assertEquals(VaultPresence.EXEMPT, presence);
        assertFalse(VaultRules.shouldReturnFromVaultLevel(presence, 1000));
    }

    // a non-listener (or spectator) left behind in a deleted vault level
    @Test
    void everyoneLeavesALevelWhoseVaultIsGone() {
        assertEquals(VaultPresence.VAULT_GONE, VaultRules.classifyPresence(VAULT, false, false, false, false));
        assertEquals(VaultPresence.VAULT_GONE, VaultRules.classifyPresence(VAULT, false, false, true, false));
        assertTrue(VaultRules.shouldReturnFromVaultLevel(VaultPresence.VAULT_GONE, VaultRules.NON_LISTENER_GRACE_TICKS));
    }

    @Test
    void deadOrTeleportingPlayersAreLeftToTheirRespawnOrTeleport() {
        assertEquals(VaultPresence.EXEMPT, VaultRules.classifyPresence(VAULT, true, false, false, true));
        assertEquals(VaultPresence.EXEMPT, VaultRules.classifyPresence(VAULT, false, false, false, true));
    }

    // V2/V3: a vault portal crossing is predicted, everything else into or out of a vault is not
    @Test
    void onlyVaultPortalCrossingsIntoOrOutOfAVaultArePredicted() {
        assertTrue(VaultRules.mayPredictCrossing(OVERWORLD, VAULT, true));
        assertTrue(VaultRules.mayPredictCrossing(VAULT, OVERWORLD, true));
        assertFalse(VaultRules.mayPredictCrossing(OVERWORLD, VAULT, false));
        assertFalse(VaultRules.mayPredictCrossing(VAULT, OVERWORLD, false));
        assertTrue(VaultRules.mayPredictCrossing(OVERWORLD, NETHER, false));
    }

    // 4.16 spectators and every non-player keep out of vault portals
    @Test
    void onlyNonSpectatorPlayersUseVaultPortals() {
        assertTrue(VaultRules.mayUseVaultPortal(true, false));
        assertFalse(VaultRules.mayUseVaultPortal(true, true));
        assertFalse(VaultRules.mayUseVaultPortal(false, false));
    }

    // V2: the crossing runs Vault Hunters' join; only a join that made the player a runner lets them in
    @Test
    void entryIsAdmittedOnlyWhenTheJoinMadeANewRunner() {
        assertTrue(VaultRules.isVaultPortalCrossingAdmitted(VaultPortalRole.ENTRY, false, true));
        // party reserve, capacity, level, re-entry or cooldown refused the join: the player stays in the hub
        assertFalse(VaultRules.isVaultPortalCrossingAdmitted(VaultPortalRole.ENTRY, false, false));
        // already a runner who is still outside: Vault Hunters pulls them in itself, so onTeleport runs only once
        assertFalse(VaultRules.isVaultPortalCrossingAdmitted(VaultPortalRole.ENTRY, true, true));
    }

    // V3: the crossing runs Vault Hunters' portal collide; only a bail or exit that removed the runner lets them out
    @Test
    void exitIsAdmittedOnlyWhenVaultHuntersRemovedTheRunner() {
        assertTrue(VaultRules.isVaultPortalCrossingAdmitted(VaultPortalRole.EXIT, true, false));
        // locked bail, inactive exit objective or cooldown: the runner stays in the vault
        assertFalse(VaultRules.isVaultPortalCrossingAdmitted(VaultPortalRole.EXIT, true, true));
        // spectators and other non-runners in the vault never leave through the window
        assertFalse(VaultRules.isVaultPortalCrossingAdmitted(VaultPortalRole.EXIT, false, false));
    }

    // a runner arrives right at the start-room window; stepping back within three seconds does not bail them
    @Test
    void anExitRightAfterAWindowEntryIsHeldBack() {
        assertTrue(VaultRules.isExitTooSoonAfterEntry(0));
        assertTrue(VaultRules.isExitTooSoonAfterEntry(VaultRules.EXIT_AFTER_ENTRY_DELAY_TICKS - 1));
        assertFalse(VaultRules.isExitTooSoonAfterEntry(VaultRules.EXIT_AFTER_ENTRY_DELAY_TICKS));
        assertFalse(VaultRules.isExitTooSoonAfterEntry(Long.MAX_VALUE));
    }

    // the crossing of a player at a window is the window's; Vault Hunters' own touch is the fallback for a player who
    // stays behind the window without crossing
    @Test
    void vaultHuntersTouchWaitsForTheWindowCrossing() {
        long never = Long.MAX_VALUE;
        assertTrue(VaultRules.isTouchLeftToWindow(true, 0, false, never));
        // the server already sees the player a tick past the plane while the crossing is on its way
        assertTrue(VaultRules.isTouchLeftToWindow(false, 1, false, never));
        assertFalse(VaultRules.isTouchLeftToWindow(false, VaultRules.WINDOW_FALLBACK_DELAY_TICKS, false, never));
        assertFalse(VaultRules.isTouchLeftToWindow(false, never, false, never));
        // a runner who has just come in stands in the start-room portal and is not bailed by it
        assertTrue(VaultRules.isTouchLeftToWindow(false, never, true, 5));
        assertFalse(VaultRules.isTouchLeftToWindow(false, never, false, 5));
    }

    @Test
    void playersOutsideVaultLevelsAreNotChecked() {
        assertEquals(VaultPresence.OUTSIDE_VAULT_LEVEL, VaultRules.classifyPresence(OVERWORLD, false, false, false, false));
        assertEquals(VaultPresence.OUTSIDE_VAULT_LEVEL, VaultRules.classifyPresence(ARENA, false, false, false, false));
    }
}
