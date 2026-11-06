package qouteall.imm_ptl.core.compat;

import net.minecraft.world.entity.Entity;
import net.minecraftforge.fml.ModList;
import qouteall.q_misc_util.Helper;

import javax.annotation.Nullable;

/**
 * Steam Rails sends its conductors to every player in their level (its own entity tracking mixin, which Immersive
 * Portals' entity tracking replaces), so a player can look through a far-away conductor with the remote lens.
 */
public class SteamRailsCompat {
    private static final String CONDUCTOR_CLASS_NAME = "com.railwayteam.railways.content.conductor.ConductorEntity";
    
    @Nullable
    private static Class<?> conductorClass;
    
    public static void init() {
        if (!ModList.get().isLoaded("railways")) {
            return;
        }
        try {
            conductorClass = Class.forName(CONDUCTOR_CLASS_NAME, false, SteamRailsCompat.class.getClassLoader());
        }
        catch (ClassNotFoundException e) {
            throw new IllegalStateException("Steam Rails is loaded but has no " + CONDUCTOR_CLASS_NAME, e);
        }
        Helper.log("Steam Rails is present");
    }
    
    public static boolean isTrackedLevelWide(Entity entity) {
        return conductorClass != null && conductorClass.isInstance(entity);
    }
}
