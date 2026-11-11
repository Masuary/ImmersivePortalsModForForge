package qouteall.imm_ptl.core.update;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import org.apache.maven.artifact.versioning.ComparableVersion;

import javax.annotation.Nullable;
import java.util.Optional;

public final class UpdateRules {
    public record AvailableUpdate(String version, String summary, String homepage) {
    }

    private UpdateRules() {
    }

    /**
     * Reads a Forge update JSON and returns its release for {@code minecraftVersion} when it is newer than
     * {@code currentVersion}. The release is promos {@code <minecraft>-recommended}, else {@code <minecraft>-latest};
     * versions compare as Forge's own version check compares them.
     *
     * @throws IllegalArgumentException when the text is not an update JSON
     */
    public static Optional<AvailableUpdate> findNewerRelease(
        String updateJson, String minecraftVersion, String currentVersion
    ) {
        JsonObject root = parseObject(updateJson);
        JsonElement promos = root.get("promos");
        if (promos == null || !promos.isJsonObject()) {
            throw new IllegalArgumentException("the update JSON has no promos object");
        }
        String release = readString(promos.getAsJsonObject(), minecraftVersion + "-recommended");
        if (release == null) {
            release = readString(promos.getAsJsonObject(), minecraftVersion + "-latest");
        }
        if (release == null || !isNewer(release, currentVersion)) {
            return Optional.empty();
        }
        String summary = null;
        JsonElement changes = root.get(minecraftVersion);
        if (changes != null && changes.isJsonObject()) {
            summary = readString(changes.getAsJsonObject(), release);
        }
        String homepage = readString(root, "homepage");
        return Optional.of(new AvailableUpdate(release, summary == null ? "" : summary, homepage == null ? "" : homepage));
    }

    public static boolean isNewer(String candidateVersion, String currentVersion) {
        return new ComparableVersion(candidateVersion).compareTo(new ComparableVersion(currentVersion)) > 0;
    }

    private static JsonObject parseObject(String json) {
        try {
            JsonElement parsed = JsonParser.parseString(json);
            if (!parsed.isJsonObject()) {
                throw new IllegalArgumentException("the update JSON is not an object");
            }
            return parsed.getAsJsonObject();
        }
        catch (JsonParseException exception) {
            throw new IllegalArgumentException("the update JSON cannot be read: " + exception.getMessage(), exception);
        }
    }

    @Nullable
    private static String readString(JsonObject object, String key) {
        JsonElement value = object.get(key);
        return value != null && value.isJsonPrimitive() ? value.getAsString() : null;
    }
}
