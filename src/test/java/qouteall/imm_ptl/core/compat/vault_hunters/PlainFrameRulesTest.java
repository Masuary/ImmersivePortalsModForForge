package qouteall.imm_ptl.core.compat.vault_hunters;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import qouteall.imm_ptl.core.compat.vault_hunters.PlainFrameRules.ToggleItem;
import qouteall.imm_ptl.core.compat.vault_hunters.PlainFrameRules.ToggleItemKind;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlainFrameRulesTest {

    @AfterEach
    void restoreDefaultToggleItem() {
        PlainFrameRules.configure(PlainFrameRules.DEFAULT_TOGGLE_ITEM);
    }

    @Test
    void theToggleItemIsAnItemIdAnItemTagOrNothing() {
        assertEquals(new ToggleItem(ToggleItemKind.ITEM, new ResourceLocation("minecraft", "glowstone_dust")),
            PlainFrameRules.parseToggleItem("minecraft:glowstone_dust"));
        assertEquals(new ToggleItem(ToggleItemKind.TAG, new ResourceLocation("forge", "dusts/glowstone")),
            PlainFrameRules.parseToggleItem("#forge:dusts/glowstone"));
        assertEquals(new ToggleItem(ToggleItemKind.ITEM, new ResourceLocation("minecraft", "stick")),
            PlainFrameRules.parseToggleItem(" stick "));
        assertEquals(new ToggleItem(ToggleItemKind.NONE, null), PlainFrameRules.parseToggleItem(""));
        assertEquals(new ToggleItem(ToggleItemKind.NONE, null), PlainFrameRules.parseToggleItem("   "));
    }

    @Test
    void aMalformedToggleItemIsRefused() {
        for (String malformed : List.of("#", "Minecraft:Glowstone", "minecraft:glowstone dust", "a:b:c", "#a b")) {
            assertFalse(PlainFrameRules.isWellFormedToggleItem(malformed), malformed);
            assertThrows(IllegalArgumentException.class, () -> PlainFrameRules.configure(malformed), malformed);
        }
        assertEquals(PlainFrameRules.parseToggleItem(PlainFrameRules.DEFAULT_TOGGLE_ITEM), PlainFrameRules.getToggleItem());
    }

    @Test
    void theMainHandTogglesFirstAndTheOffHandUseRightAfterItIsSwallowed() {
        assertEquals(InteractionHand.MAIN_HAND, PlainFrameRules.getToggleHand(true, true));
        assertEquals(InteractionHand.MAIN_HAND, PlainFrameRules.getToggleHand(true, false));
        assertEquals(InteractionHand.OFF_HAND, PlainFrameRules.getToggleHand(false, true));
        assertNull(PlainFrameRules.getToggleHand(false, false));

        assertTrue(PlainFrameRules.isOffHandUseAfterToggle(InteractionHand.OFF_HAND, InteractionHand.MAIN_HAND));
        assertFalse(PlainFrameRules.isOffHandUseAfterToggle(InteractionHand.MAIN_HAND, InteractionHand.MAIN_HAND));
        assertFalse(PlainFrameRules.isOffHandUseAfterToggle(InteractionHand.MAIN_HAND, InteractionHand.OFF_HAND));
        assertFalse(PlainFrameRules.isOffHandUseAfterToggle(InteractionHand.OFF_HAND, InteractionHand.OFF_HAND));
    }

    @Test
    void aHeldUseKeyRepeatingEveryFourTicksStaysInTheCooldownOfItsLastAttempt() {
        for (int ticksSinceLastAttempt = 0; ticksSinceLastAttempt <= 4; ticksSinceLastAttempt++) {
            assertTrue(PlainFrameRules.isToggleCoolingDown(ticksSinceLastAttempt));
        }
        assertTrue(PlainFrameRules.isToggleCoolingDown(PlainFrameRules.TOGGLE_COOLDOWN_TICKS - 1));
        assertFalse(PlainFrameRules.isToggleCoolingDown(PlainFrameRules.TOGGLE_COOLDOWN_TICKS));
        assertFalse(PlainFrameRules.isToggleCoolingDown(Long.MAX_VALUE));
    }

    @Test
    void theItemUseRightAfterASwitchBelongsToIt() {
        assertTrue(PlainFrameRules.isItemUseOfToggle(0));
        assertTrue(PlainFrameRules.isItemUseOfToggle(1));
        assertFalse(PlainFrameRules.isItemUseOfToggle(2));
        assertFalse(PlainFrameRules.isItemUseOfToggle(Long.MAX_VALUE));
        assertFalse(PlainFrameRules.isItemUseOfToggle(-1));
    }

    @Test
    void theRingOfAFrameAcrossXIsItsSideColumnsAndBottomAndTopRows() {
        BlockPos min = new BlockPos(10, 64, 5);
        BlockPos max = new BlockPos(11, 66, 5);
        Set<BlockPos> ring = Set.copyOf(PlainFrameRules.getFrameRing(min, max, Direction.Axis.Z));
        assertEquals(Set.of(
            new BlockPos(9, 64, 5), new BlockPos(9, 65, 5), new BlockPos(9, 66, 5),
            new BlockPos(12, 64, 5), new BlockPos(12, 65, 5), new BlockPos(12, 66, 5),
            new BlockPos(10, 63, 5), new BlockPos(11, 63, 5),
            new BlockPos(10, 67, 5), new BlockPos(11, 67, 5)
        ), ring);
    }

    @Test
    void theRingOfAFrameAcrossZRunsAlongZ() {
        BlockPos min = new BlockPos(-3, 70, -8);
        BlockPos max = new BlockPos(-3, 72, -7);
        Set<BlockPos> ring = Set.copyOf(PlainFrameRules.getFrameRing(min, max, Direction.Axis.X));
        assertEquals(10, ring.size());
        assertTrue(ring.contains(new BlockPos(-3, 71, -9)));
        assertTrue(ring.contains(new BlockPos(-3, 71, -6)));
        assertTrue(ring.contains(new BlockPos(-3, 69, -8)));
        assertTrue(ring.contains(new BlockPos(-3, 73, -7)));
        assertFalse(ring.contains(new BlockPos(-3, 69, -9)), "corners are not part of the ring");
    }

    @Test
    void aRingNeedsAWallOpeningOneBlockThick() {
        assertThrows(IllegalArgumentException.class,
            () -> PlainFrameRules.getFrameRing(new BlockPos(0, 0, 0), new BlockPos(1, 2, 1), Direction.Axis.Z));
        assertThrows(IllegalArgumentException.class,
            () -> PlainFrameRules.getFrameRing(new BlockPos(0, 0, 0), new BlockPos(1, 0, 1), Direction.Axis.Y));
    }

    @Test
    void aFrameContainsTheBlocksOfItsOpeningOnly() {
        BlockPos min = new BlockPos(10, 64, 5);
        BlockPos max = new BlockPos(11, 66, 5);
        assertTrue(PlainFrameRules.contains(min, max, new BlockPos(11, 65, 5)));
        assertFalse(PlainFrameRules.contains(min, max, new BlockPos(12, 65, 5)));
        assertFalse(PlainFrameRules.contains(min, max, new BlockPos(10, 64, 6)));
    }
}
