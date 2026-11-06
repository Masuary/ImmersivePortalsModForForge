package qouteall.imm_ptl.core.portal;

import net.minecraft.ResourceLocationException;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * The server's switch to turn portals off for some dimensions (config {@code portalDisabledDimensions}): a portal in or
 * into a listed dimension is not shown to players, loads no chunks remotely and teleports nothing. An entry is a
 * dimension id ({@code namespace:path}) or a whole namespace ({@code namespace}).
 */
public final class PortalDimensionFilter {
    private static volatile Set<ResourceLocation> disabledDimensions = Set.of();
    private static volatile Set<String> disabledNamespaces = Set.of();
    
    private PortalDimensionFilter() {
    }
    
    public static boolean isValidEntry(Object entry) {
        if (!(entry instanceof String text) || text.isEmpty()) {
            return false;
        }
        return ResourceLocation.isValidResourceLocation(text.contains(":") ? text : text + ":dimension");
    }
    
    public static void configure(List<? extends String> entries) {
        for (String entry : entries) {
            if (!isValidEntry(entry)) {
                throw new ResourceLocationException("Not a dimension id or namespace in portalDisabledDimensions: " + entry);
            }
        }
        disabledDimensions = entries.stream().filter(entry -> entry.contains(":"))
            .map(ResourceLocation::new).collect(Collectors.toUnmodifiableSet());
        disabledNamespaces = entries.stream().filter(entry -> !entry.contains(":"))
            .collect(Collectors.toUnmodifiableSet());
    }
    
    public static boolean isDisabled(ResourceLocation dimension) {
        return disabledDimensions.contains(dimension) || disabledNamespaces.contains(dimension.getNamespace());
    }
    
    public static boolean isPortalDisabled(ResourceKey<Level> from, ResourceKey<Level> to) {
        return isDisabled(from.location()) || isDisabled(to.location());
    }
}
