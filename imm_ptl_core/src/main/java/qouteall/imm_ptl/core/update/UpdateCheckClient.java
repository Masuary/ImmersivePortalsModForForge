package qouteall.imm_ptl.core.update;

import net.minecraft.client.Minecraft;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.common.MinecraftForge;

/**
 * Tells the player once per game launch, when they enter a world, that a newer release exists.
 */
public final class UpdateCheckClient {
    private static boolean shownThisLaunch;

    private UpdateCheckClient() {
    }

    public static void init() {
        MinecraftForge.EVENT_BUS.addListener(UpdateCheckClient::onLoggedIn);
    }

    private static void onLoggedIn(ClientPlayerNetworkEvent.LoggedInEvent event) {
        Minecraft minecraft = Minecraft.getInstance();
        UpdateCheck.getResult().thenAccept(update -> update.ifPresent(available -> minecraft.execute(() -> {
            if (shownThisLaunch || minecraft.player == null) {
                return;
            }
            shownThisLaunch = true;
            minecraft.gui.getChat().addMessage(UpdateCheck.describe(available, "you have"));
        })));
    }
}
