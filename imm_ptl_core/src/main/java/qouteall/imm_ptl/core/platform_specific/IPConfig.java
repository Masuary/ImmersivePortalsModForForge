package qouteall.imm_ptl.core.platform_specific;

import net.minecraft.world.item.Items;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.config.ModConfigEvent;
import net.minecraftforge.registries.ForgeRegistries;
import qouteall.imm_ptl.core.IPGlobal;
import qouteall.imm_ptl.core.compat.vault_hunters.PlainFrameRules;
import qouteall.imm_ptl.core.compat.vault_hunters.VaultPreviewRules;
import qouteall.imm_ptl.core.portal.PortalDimensionFilter;
import qouteall.imm_ptl.core.update.UpdateCheck;
import qouteall.q_misc_util.Helper;
import qouteall.q_misc_util.dimension.DimensionMisc;

import java.util.List;

public class IPConfig {

    public static ForgeConfigSpec.BooleanValue enableWarning;
    public static ForgeConfigSpec.BooleanValue enableMirrorCreation;
    public static ForgeConfigSpec.IntValue maxPortalLayer;
    public static ForgeConfigSpec.BooleanValue sharedBlockMeshBufferOptimization;
    public static ForgeConfigSpec.BooleanValue lagAttackProof;
    public static ForgeConfigSpec.IntValue portalRenderLimit;
    public static ForgeConfigSpec.IntValue indirectLoadingRadiusCap;
    public static ForgeConfigSpec.BooleanValue enableCrossPortalSound;
    public static ForgeConfigSpec.BooleanValue compatibilityRenderMode;
    public static ForgeConfigSpec.BooleanValue doCheckGlError;
    public static ForgeConfigSpec.IntValue portalSearchingRange;
    public static ForgeConfigSpec.BooleanValue renderYourselfInPortal;
    public static ForgeConfigSpec.BooleanValue serverSideNormalChunkLoading;
    public static ForgeConfigSpec.BooleanValue teleportationDebug;
    public static ForgeConfigSpec.BooleanValue correctCrossPortalEntityRendering;
    public static ForgeConfigSpec.BooleanValue looseMovementCheck;
    public static ForgeConfigSpec.BooleanValue pureMirror;
    public static ForgeConfigSpec.BooleanValue enableAlternateDimensions;
    public static ForgeConfigSpec.BooleanValue reducedPortalRendering;
    public static ForgeConfigSpec.BooleanValue visibilityPrediction;
    public static ForgeConfigSpec.BooleanValue netherPortalOverlay;
    public static ForgeConfigSpec.IntValue scaleLimit;
    public static ForgeConfigSpec.BooleanValue easeCreativePermission;
    public static ForgeConfigSpec.BooleanValue easeCommandStickPermission;
    public static ForgeConfigSpec.BooleanValue enableDatapackPortalGen;
    public static ForgeConfigSpec.BooleanValue enableCrossPortalView;
    public static ForgeConfigSpec.BooleanValue enableClippingMechanism;
    public static ForgeConfigSpec.BooleanValue enableDepthClampForPortalRendering;
    public static ForgeConfigSpec.BooleanValue lightVanillaNetherPortalWhenCrouching;
    public static ForgeConfigSpec.BooleanValue enableNetherPortalEffect;
    public static ForgeConfigSpec.BooleanValue enableClientPerformanceAdjustment;
    public static ForgeConfigSpec.BooleanValue enableServerPerformanceAdjustment;
    public static ForgeConfigSpec.EnumValue<IPGlobal.NetherPortalMode> netherPortalMode;
    public static ForgeConfigSpec.EnumValue<IPGlobal.EndPortalMode> endPortalMode;
    public static ForgeConfigSpec.BooleanValue enableModelDataFix;
    public static ForgeConfigSpec.BooleanValue editGlobalDimensionStack;
    public static ForgeConfigSpec.BooleanValue restoreMissingVanillaDimensions;
    public static ForgeConfigSpec.ConfigValue<List<? extends String>> portalDisabledDimensions;
    public static ForgeConfigSpec.EnumValue<VaultPreviewRules.Mode> vaultPreview;
    public static ForgeConfigSpec.IntValue vaultPreviewTimeoutMinutes;
    public static ForgeConfigSpec.ConfigValue<String> vaultWindowToggleItem;
    public static ForgeConfigSpec.BooleanValue checkForUpdates;

    public static void register(ForgeConfigSpec.Builder builder) {
        builder.comment("Check the wiki at https://qouteall.fun/immptl/wiki/Config-Options for more information");
        enableWarning = builder
            .comment("Warn in chat and in the log about mods that may be incompatible (off in this fork: the pack is tested)")
            .define("enableWarning", false);
        enableMirrorCreation = builder.define("enableMirrowCreation", true);
        maxPortalLayer = builder.defineInRange("maxPortalLayer", 5, 0, Integer.MAX_VALUE);
        sharedBlockMeshBufferOptimization = builder.define("sharedBlockMeshBufferOptimization", true);
        lagAttackProof = builder.define("lagAttackProof", true);
        portalRenderLimit = builder.defineInRange("portalRenderLimit", 200, 0, Integer.MAX_VALUE);
        indirectLoadingRadiusCap = builder.defineInRange("indirectLoadingRadiusCap", 8, 0, Integer.MAX_VALUE);
        enableCrossPortalSound = builder.define("enableCrossPortalSound", true);
        enableModelDataFix = builder.define("enableModelDataFix", true);
        compatibilityRenderMode = builder.define("compatibilityRenderMode", false);
        doCheckGlError = builder.define("doCheckGlError", false);
        portalSearchingRange = builder.defineInRange("portalSearchingRange", 128, 0, Integer.MAX_VALUE);
        renderYourselfInPortal = builder.define("renderYourselfInPortal", true);
        serverSideNormalChunkLoading = builder.define("serverSideNormalChunkLoading", true);
        teleportationDebug = builder.define("teleportationDebug", false);
        correctCrossPortalEntityRendering = builder.define("correctCrossPortalEntityRendering", true);
        looseMovementCheck = builder.define("looseMovementCheck", false);
        pureMirror = builder.define("pureMirror", false);
        enableAlternateDimensions = builder.define("enableAlternateDimensions", true);
        reducedPortalRendering = builder.define("reducedPortalRendering", false);
        visibilityPrediction = builder.define("visibilityPrediction", true);
        netherPortalOverlay = builder.define("netherPortalOverlay", false);
        scaleLimit = builder.defineInRange("scaleLimit", 30, Integer.MIN_VALUE, Integer.MAX_VALUE);
        easeCreativePermission = builder.define("easeCreativePermission", true);
        easeCommandStickPermission = builder.define("easeCommandStickPermission", false);
        enableDatapackPortalGen = builder.define("enableDatapackPortalGen", true);
        enableCrossPortalView = builder.define("enableCrossPortalView", true);
        enableClippingMechanism = builder.define("enableClippingMechanism", true);
        enableDepthClampForPortalRendering = builder.define("enableDepthClampForPortalRendering", true);
        lightVanillaNetherPortalWhenCrouching = builder.define("lightVanillaNetherPortalWhenCrouching", false);
        enableNetherPortalEffect = builder.define("enableNetherPortalEffect", true);
        enableClientPerformanceAdjustment = builder.define("enableClientPerformanceAdjustment", true);
        enableServerPerformanceAdjustment = builder.define("enableServerPerformanceAdjustment", true);
        netherPortalMode = builder.defineEnum("netherPortalMode", IPGlobal.NetherPortalMode.normal, IPGlobal.NetherPortalMode.values());
        endPortalMode = builder.defineEnum("endPortalMode", IPGlobal.EndPortalMode.normal, IPGlobal.EndPortalMode.values());
        editGlobalDimensionStack = builder.define("enableGlobalDimensionStack", false);
        restoreMissingVanillaDimensions = builder
            .comment("Recreate the Nether and the End when the world has none (repairs worlds that lost them to DFU)")
            .define("restoreMissingVanillaDimensions", false);
        portalDisabledDimensions = builder
            .comment("Dimensions (\"namespace:path\") or whole namespaces (\"namespace\") where portals are turned off: a portal in or into one is not shown, loads no chunks and teleports nothing (\"the_vault\" turns off the see-through Vault Hunters portals)")
            .defineList("portalDisabledDimensions", List.of(), PortalDimensionFilter::isValidEntry);
        vaultPreview = builder
            .comment("Vault Hunters: lighting a hub frame creates its vault at once, so the frame shows it before anyone enters. off = Vault Hunters' own behaviour (the vault is made on the first touch); untilBroken = an unentered vault stays until its frame is broken; untilRestart = also dropped when the server restarts; timeout = also dropped after vaultPreviewTimeoutMinutes. A dropped vault's frame stays lit and makes a fresh vault on the next touch")
            .defineEnum("vaultPreview", VaultPreviewRules.Mode.untilBroken, VaultPreviewRules.Mode.values());
        vaultPreviewTimeoutMinutes = builder
            .comment("Minutes an unentered vault preview stays with vaultPreview = timeout")
            .defineInRange("vaultPreviewTimeoutMinutes", 5, 1, 10080);
        vaultWindowToggleItem = builder
            .comment("Vault Hunters: sneaking and using this item on a lit hub frame switches its vault window off (Vault Hunters' own portal for everyone) or back on; only a player who could break the frame may switch it, and the item is not used up. An item id (\"minecraft:glowstone_dust\"), an item tag (\"#forge:dusts/glowstone\") or empty to turn the switch off. A value naming no item is reset to the default with a warning. To turn vault windows off everywhere, add \"the_vault\" to portalDisabledDimensions")
            .define("vaultWindowToggleItem", PlainFrameRules.DEFAULT_TOGGLE_ITEM, IPConfig::isValidVaultWindowToggleItem);
        checkForUpdates = builder
            .comment("Look for a newer release of Immersive Portals for Vault Hunters once per start (reads a small file on GitHub). A newer release is written to the log; on a dedicated server operators get a chat line when they join, and players get one when they enter a world. Read at startup")
            .define("checkForUpdates", true);

        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, builder.build());
    }

    @SubscribeEvent
    public static void onConfigChanged(ModConfigEvent.Reloading event) {
        Helper.log("IP Config Reloading");
        loadConfig();
    }

    @SubscribeEvent
    public static void onConfigLoad(ModConfigEvent.Loading event) {
        loadConfig();
    }

    public static void loadConfig() {
        if (compatibilityRenderMode.get()) {
            IPGlobal.renderMode = IPGlobal.RenderMode.compatibility;
        }
        else {
            IPGlobal.renderMode = IPGlobal.RenderMode.normal;
        }
        IPGlobal.enableWarning = enableWarning.get();
        IPGlobal.enableMirrorCreation = enableMirrorCreation.get();
        IPGlobal.doCheckGlError = doCheckGlError.get();
        IPGlobal.maxPortalLayer = maxPortalLayer.get();
        IPGlobal.lagAttackProof = lagAttackProof.get();
        IPGlobal.portalRenderLimit = portalRenderLimit.get();
        IPGlobal.netherPortalFindingRadius = portalSearchingRange.get();
        IPGlobal.renderYourselfInPortal = renderYourselfInPortal.get();
        IPGlobal.activeLoading = serverSideNormalChunkLoading.get();
        IPGlobal.teleportationDebugEnabled = teleportationDebug.get();
        IPGlobal.correctCrossPortalEntityRendering = correctCrossPortalEntityRendering.get();
        IPGlobal.looseMovementCheck = looseMovementCheck.get();
        IPGlobal.pureMirror = pureMirror.get();
        IPGlobal.enableAlternateDimensions = enableAlternateDimensions.get();
        IPGlobal.indirectLoadingRadiusCap = indirectLoadingRadiusCap.get();
        IPGlobal.netherPortalMode = netherPortalMode.get();
        IPGlobal.endPortalMode = endPortalMode.get();
        IPGlobal.reducedPortalRendering = reducedPortalRendering.get();
        IPGlobal.offsetOcclusionQuery = visibilityPrediction.get();
        IPGlobal.netherPortalOverlay = netherPortalOverlay.get();
        IPGlobal.scaleLimit = scaleLimit.get();
        IPGlobal.easeCreativePermission = easeCreativePermission.get();
        IPGlobal.enableSharedBlockMeshBuffers = sharedBlockMeshBufferOptimization.get();
        IPGlobal.enableDatapackPortalGen = enableDatapackPortalGen.get();
        IPGlobal.enableCrossPortalView = enableCrossPortalView.get();
        IPGlobal.enableClippingMechanism = enableClippingMechanism.get();
        IPGlobal.lightVanillaNetherPortalWhenCrouching = lightVanillaNetherPortalWhenCrouching.get();
        IPGlobal.enableNetherPortalEffect = enableNetherPortalEffect.get();
        IPGlobal.enableClientPerformanceAdjustment = enableClientPerformanceAdjustment.get();
        IPGlobal.enableServerPerformanceAdjustment = enableServerPerformanceAdjustment.get();
        IPGlobal.enableCrossPortalSound = enableCrossPortalSound.get();
        IPGlobal.enableModelDataFix = enableModelDataFix.get();

        if (Boolean.TRUE.equals(enableDepthClampForPortalRendering.get())) {
            IPGlobal.enableDepthClampForPortalRendering = true;
        }

        IPGlobal.editGlobalDimensionStack = editGlobalDimensionStack.get();
        DimensionMisc.restoreMissingVanillaDimensions = restoreMissingVanillaDimensions.get();
        PortalDimensionFilter.configure(portalDisabledDimensions.get());
        VaultPreviewRules.configure(vaultPreview.get(), vaultPreviewTimeoutMinutes.get());
        PlainFrameRules.configure(vaultWindowToggleItem.get());
        UpdateCheck.configure(checkForUpdates.get());

        Helper.log("IP Config Applied");
    }

    // items are registered before the common config loads; an item tag can only be checked once tags load (PlainFrames)
    private static boolean isValidVaultWindowToggleItem(Object value) {
        if (!(value instanceof String text) || !PlainFrameRules.isWellFormedToggleItem(text)) {
            return false;
        }
        PlainFrameRules.ToggleItem toggleItem = PlainFrameRules.parseToggleItem(text);
        return toggleItem.kind() != PlainFrameRules.ToggleItemKind.ITEM
            || (ForgeRegistries.ITEMS.containsKey(toggleItem.id()) && ForgeRegistries.ITEMS.getValue(toggleItem.id()) != Items.AIR);
    }
    
}
