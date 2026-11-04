package qouteall.imm_ptl.core.compat.vault_hunters;

import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
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

    @Test
    void playersOutsideVaultLevelsAreNotChecked() {
        assertEquals(VaultPresence.OUTSIDE_VAULT_LEVEL, VaultRules.classifyPresence(OVERWORLD, false, false, false, false));
        assertEquals(VaultPresence.OUTSIDE_VAULT_LEVEL, VaultRules.classifyPresence(ARENA, false, false, false, false));
    }
}
