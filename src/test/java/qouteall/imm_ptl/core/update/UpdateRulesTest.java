package qouteall.imm_ptl.core.update;

import org.junit.jupiter.api.Test;
import qouteall.imm_ptl.core.update.UpdateRules.AvailableUpdate;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UpdateRulesTest {
    private static final String UPDATE_JSON = """
        {
          "homepage": "https://www.curseforge.com/projects/1787636",
          "promos": {
            "1.18.2-latest": "1.4.20-vh3r.8",
            "1.18.2-recommended": "1.4.20-vh3r.8"
          },
          "1.18.2": {
            "1.4.20-vh3r.8": "A newer release.",
            "1.4.20-vh3r.7": "The release before."
          }
        }
        """;

    @Test
    void anOlderInstallGetsTheRecommendedRelease() {
        Optional<AvailableUpdate> update = UpdateRules.findNewerRelease(UPDATE_JSON, "1.18.2", "1.4.20-vh3r.7");
        assertTrue(update.isPresent());
        assertEquals("1.4.20-vh3r.8", update.get().version());
        assertEquals("A newer release.", update.get().summary());
        assertEquals("https://www.curseforge.com/projects/1787636", update.get().homepage());
    }

    @Test
    void theSameOrANewerInstallGetsNothing() {
        assertFalse(UpdateRules.findNewerRelease(UPDATE_JSON, "1.18.2", "1.4.20-vh3r.8").isPresent());
        assertFalse(UpdateRules.findNewerRelease(UPDATE_JSON, "1.18.2", "1.4.20-vh3r.9").isPresent());
    }

    @Test
    void twoDigitReleaseNumbersCompareAsNumbers() {
        assertTrue(UpdateRules.isNewer("1.4.20-vh3r.10", "1.4.20-vh3r.9"));
        assertFalse(UpdateRules.isNewer("1.4.20-vh3r.9", "1.4.20-vh3r.10"));
        assertTrue(UpdateRules.isNewer("1.4.21-vh3r.1", "1.4.20-vh3r.12"));
    }

    @Test
    void latestIsUsedWithoutARecommendedRelease() {
        String json = """
            {"promos": {"1.18.2-latest": "1.4.20-vh3r.8"}}
            """;
        Optional<AvailableUpdate> update = UpdateRules.findNewerRelease(json, "1.18.2", "1.4.20-vh3r.7");
        assertTrue(update.isPresent());
        assertEquals("", update.get().summary());
        assertEquals("", update.get().homepage());
    }

    @Test
    void anotherMinecraftVersionGetsNothing() {
        assertFalse(UpdateRules.findNewerRelease(UPDATE_JSON, "1.19.2", "1.4.20-vh3r.1").isPresent());
    }

    @Test
    void textThatIsNoUpdateJsonIsRefused() {
        assertThrows(IllegalArgumentException.class, () -> UpdateRules.findNewerRelease("<html>", "1.18.2", "1.0"));
        assertThrows(IllegalArgumentException.class, () -> UpdateRules.findNewerRelease("[]", "1.18.2", "1.0"));
        assertThrows(IllegalArgumentException.class, () -> UpdateRules.findNewerRelease("{}", "1.18.2", "1.0"));
    }
}
