package qouteall.imm_ptl.core.portal;

import net.minecraft.ResourceLocationException;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PortalDimensionFilterTest {
    @AfterEach
    void turnEverythingBackOn() {
        PortalDimensionFilter.configure(List.of());
    }

    @Test
    void nothingIsTurnedOffByDefault() {
        assertFalse(PortalDimensionFilter.isDisabled(new ResourceLocation("the_vault", "vault_0")));
        assertFalse(PortalDimensionFilter.isDisabled(new ResourceLocation("minecraft", "overworld")));
    }

    @Test
    void aNamespaceTurnsOffEveryDimensionInIt() {
        PortalDimensionFilter.configure(List.of("the_vault"));

        assertTrue(PortalDimensionFilter.isDisabled(new ResourceLocation("the_vault", "vault_6f1c")));
        assertTrue(PortalDimensionFilter.isDisabled(new ResourceLocation("the_vault", "arena")));
        assertFalse(PortalDimensionFilter.isDisabled(new ResourceLocation("minecraft", "overworld")));
    }

    @Test
    void aDimensionIdTurnsOffOnlyThatDimension() {
        PortalDimensionFilter.configure(List.of("minecraft:the_nether"));

        assertTrue(PortalDimensionFilter.isDisabled(new ResourceLocation("minecraft", "the_nether")));
        assertFalse(PortalDimensionFilter.isDisabled(new ResourceLocation("minecraft", "the_end")));
    }

    @Test
    void entriesMustBeDimensionIdsOrNamespaces() {
        assertTrue(PortalDimensionFilter.isValidEntry("the_vault"));
        assertTrue(PortalDimensionFilter.isValidEntry("immersive_portals:alternate1"));
        assertFalse(PortalDimensionFilter.isValidEntry("The Vault"));
        assertFalse(PortalDimensionFilter.isValidEntry(""));
        assertFalse(PortalDimensionFilter.isValidEntry(42));
        assertThrows(ResourceLocationException.class, () -> PortalDimensionFilter.configure(List.of("Not:Valid")));
    }
}
