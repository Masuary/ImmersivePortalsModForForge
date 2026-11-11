package qouteall.imm_ptl.core.update;

import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.versions.mcp.MCPVersion;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import qouteall.imm_ptl.core.platform_specific.IPModEntry;
import qouteall.imm_ptl.core.update.UpdateRules.AvailableUpdate;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Looks for a newer release once per game or server start, in the background: the packs this fork supports turn Forge's
 * own version check off. The result goes to the log; operators of a dedicated server get a chat line when they join,
 * and a client gets one when it enters a world ({@link UpdateCheckClient}).
 */
public final class UpdateCheck {
    public static final String UPDATE_JSON_URL =
        "https://raw.githubusercontent.com/Masuary/ImmersivePortalsModForForge/1.18-vh3r/update.json";
    private static final String MOD_NAME = "Immersive Portals for Vault Hunters";
    private static final Duration TIMEOUT = Duration.ofSeconds(10);
    private static final Logger LOGGER = LogManager.getLogger();
    private static final CompletableFuture<Optional<AvailableUpdate>> RESULT = new CompletableFuture<>();
    private static final AtomicBoolean STARTED = new AtomicBoolean();
    private static volatile boolean enabled = true;

    private UpdateCheck() {
    }

    public static void init() {
        FMLJavaModLoadingContext.get().getModEventBus().addListener((FMLCommonSetupEvent event) -> start());
        MinecraftForge.EVENT_BUS.addListener(UpdateCheck::onPlayerLoggedIn);
    }

    public static void configure(boolean checkForUpdates) {
        enabled = checkForUpdates;
    }

    /**
     * Completes once the check is done: empty when the check is off, failed, or no newer release exists.
     */
    public static CompletableFuture<Optional<AvailableUpdate>> getResult() {
        return RESULT;
    }

    public static String getCurrentVersion() {
        return ModList.get().getModContainerById(IPModEntry.MODID)
            .map(container -> container.getModInfo().getVersion().toString())
            .orElseThrow(() -> new IllegalStateException("mod " + IPModEntry.MODID + " is not loaded"));
    }

    // one chat line whatever the summary's length: the summary is the line's hover text
    public static MutableComponent describe(AvailableUpdate update, String installedLabel) {
        MutableComponent versionText = new TextComponent(update.version() + " is available (" + installedLabel + " "
            + getCurrentVersion() + ")").withStyle(ChatFormatting.WHITE);
        if (!update.summary().isEmpty()) {
            versionText.withStyle(style -> style.withHoverEvent(
                new HoverEvent(HoverEvent.Action.SHOW_TEXT, new TextComponent(update.summary()))));
        }
        MutableComponent message = new TextComponent("[" + MOD_NAME + "] ").withStyle(ChatFormatting.GOLD)
            .append(versionText);
        if (!update.homepage().isEmpty()) {
            message.append(" ").append(new TextComponent("[Download]").withStyle(style -> style
                .withColor(ChatFormatting.AQUA)
                .withClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, update.homepage()))
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new TextComponent(update.homepage())))));
        }
        return message;
    }

    private static void start() {
        if (!STARTED.compareAndSet(false, true)) {
            return;
        }
        if (!enabled) {
            RESULT.complete(Optional.empty());
            return;
        }
        String currentVersion = getCurrentVersion();
        HttpRequest request = HttpRequest.newBuilder(URI.create(UPDATE_JSON_URL))
            .timeout(TIMEOUT)
            .header("User-Agent", "ImmersivePortalsForVaultHunters/" + currentVersion)
            .GET()
            .build();
        HttpClient.newBuilder().connectTimeout(TIMEOUT).build()
            .sendAsync(request, HttpResponse.BodyHandlers.ofString())
            .handle((response, failure) -> RESULT.complete(readResponse(response, failure, currentVersion)));
    }

    private static Optional<AvailableUpdate> readResponse(
        HttpResponse<String> response, Throwable failure, String currentVersion
    ) {
        if (failure != null) {
            Throwable cause = failure instanceof CompletionException && failure.getCause() != null ? failure.getCause() : failure;
            LOGGER.info("Could not check for {} updates: {}", MOD_NAME, cause.getMessage() == null ? cause.getClass().getSimpleName() : cause.getMessage());
            return Optional.empty();
        }
        if (response.statusCode() != 200) {
            LOGGER.info("Could not check for {} updates: {} answered HTTP {}", MOD_NAME, UPDATE_JSON_URL, response.statusCode());
            return Optional.empty();
        }
        try {
            Optional<AvailableUpdate> update =
                UpdateRules.findNewerRelease(response.body(), MCPVersion.getMCVersion(), currentVersion);
            if (update.isPresent()) {
                LOGGER.info("{} {} is available (installed {}): {}", MOD_NAME, update.get().version(), currentVersion,
                    update.get().homepage());
            }
            else {
                LOGGER.info("{} {} is up to date", MOD_NAME, currentVersion);
            }
            return update;
        }
        catch (IllegalArgumentException exception) {
            LOGGER.info("Could not check for {} updates: {}", MOD_NAME, exception.getMessage());
            return Optional.empty();
        }
    }

    // a singleplayer host learns about it from the client's own chat line
    private static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getPlayer() instanceof ServerPlayer player) || !player.server.isDedicatedServer() || !player.hasPermissions(2)) {
            return;
        }
        MinecraftServer server = player.server;
        RESULT.thenAccept(update -> update.ifPresent(available -> server.execute(() -> {
            if (server.getPlayerList().getPlayer(player.getUUID()) == player) {
                Component message = describe(available, "this server has");
                player.sendMessage(message, Util.NIL_UUID);
            }
        })));
    }
}
