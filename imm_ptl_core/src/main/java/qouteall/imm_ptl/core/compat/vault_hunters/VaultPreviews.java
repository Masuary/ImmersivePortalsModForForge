package qouteall.imm_ptl.core.compat.vault_hunters;

import iskallia.vault.block.VaultPortalBlock;
import iskallia.vault.block.entity.VaultPortalTileEntity;
import iskallia.vault.core.Version;
import iskallia.vault.core.vault.Vault;
import iskallia.vault.core.vault.VaultFactory;
import iskallia.vault.core.world.storage.VirtualWorld;
import iskallia.vault.item.crystal.CrystalData;
import iskallia.vault.world.data.ServerVaults;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.server.ServerLifecycleHooks;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import qouteall.imm_ptl.core.portal.PortalDimensionFilter;

import javax.annotation.Nullable;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Lit hub frames show their vault before anyone enters (owner 2026-11-06): lighting creates the vault through Vault
 * Hunters' own factory with the lighter as its creator, as the first touch would, and links the frame at once. Vault
 * Hunters deletes a vault without listeners on its next tick, so such a preview is kept until its first runner
 * arrives ({@link #isWaitingForRunners}); {@link VaultPreviewRules} decides when an unentered one is dropped, and a
 * broken frame drops it too. A dropped preview's vault ends like an empty Vault Hunters vault and its frame is reset to
 * "lit, no vault", so the next touch creates a fresh one. Previews are saved with the overworld and survive restarts.
 */
public final class VaultPreviews {
    private static final Logger LOGGER = LogManager.getLogger();
    private static final String DATA_NAME = "imm_ptl_vault_previews";
    private static final int CHECK_INTERVAL_TICKS = 20;
    private static final ResourceLocation ANY_VAULT_LEVEL =
        new ResourceLocation(VaultRules.VAULT_HUNTERS_MOD_ID, "vault");

    private record Preview(UUID vaultId, ResourceKey<Level> hubDimension, BlockPos frameBlock, long createdAt) {
    }

    @Nullable
    private static Storage storage;
    private static boolean startupChecked;

    private VaultPreviews() {
    }

    static void init() {
        MinecraftForge.EVENT_BUS.addListener(VaultPreviews::onServerTick);
        MinecraftForge.EVENT_BUS.addListener((ServerStoppedEvent event) -> {
            storage = null;
            startupChecked = false;
        });
    }

    // the objective id Vault Hunters itself writes into the crystal ("cake", "boss", Wold's Vaults' "corrupted" ...)
    private static String getObjectiveType(CrystalData crystal) {
        return crystal.writeNbt().map(nbt -> nbt.getCompound("Objective").getString("type")).orElse("");
    }

    public static void onFrameLit(ServerLevel hub, BlockPos portalBlock, ServerPlayer lighter) {
        BlockState state = hub.getBlockState(portalBlock);
        CrystalData crystal = getCrystal(hub, portalBlock);
        if (!(state.getBlock() instanceof VaultPortalBlock frame) || crystal == null || crystal.getProperties().getVaultId() != null
            || lighter instanceof FakePlayer || ServerVaults.get(hub).isPresent()) {
            return;
        }
        boolean isVaultTurnedOff = PortalDimensionFilter.isDisabled(hub.dimension().location())
            || PortalDimensionFilter.isDisabled(ANY_VAULT_LEVEL);
        if (!VaultPreviewRules.shouldPreview(VaultPreviewRules.getMode(), VaultPortalLinks.isFloorFrame(state),
            PlainFrames.isPlain(hub, portalBlock), crystal.getProperties().getChallengeUuid() != null, isVaultTurnedOff,
            getObjectiveType(crystal))) {
            return;
        }
        Vault vault = ServerVaults.add(VaultFactory.create(Version.latest(), crystal.copy(), lighter));
        UUID vaultId = vault.get(Vault.ID);
        setFrameVaultId(hub, portalBlock, frame, vaultId);
        getStorage(hub.getServer()).add(new Preview(vaultId, hub.dimension(), portalBlock.immutable(), hub.getGameTime()));
        VaultPortalLinks.requestLink(hub, portalBlock, lighter.position(), vaultId);
        LOGGER.info("Created vault {} when {} lit the hub frame at {} in {}, so the frame shows it before anyone enters",
            vaultId, lighter.getName().getString(), portalBlock, hub.dimension().location());
    }

    /**
     * Whether Vault Hunters must leave this vault alone although it has no listeners: it is a preview whose first
     * runner has not arrived yet.
     */
    public static boolean isWaitingForRunners(Vault vault) {
        // loads the saved previews on the first call: Vault Hunters ticks restored vaults from the first server tick
        return getStorage(ServerLifecycleHooks.getCurrentServer()).previews.containsKey(vault.get(Vault.ID))
            && vault.get(Vault.LISTENERS).getAll().isEmpty();
    }

    /**
     * A frame whose preview was dropped while its chunk was unloaded still names the deleted vault; it is reset before
     * Vault Hunters handles a touch, so the touch creates a fresh vault instead of finding none.
     */
    public static void resetDroppedFrameBeforeTouch(ServerLevel hub, BlockPos portalBlock) {
        Storage previews = getStorage(hub.getServer());
        if (previews.droppedFrames.isEmpty()) {
            return;
        }
        CrystalData crystal = getCrystal(hub, portalBlock);
        UUID vaultId = crystal == null ? null : crystal.getProperties().getVaultId();
        Preview dropped = vaultId == null ? null : previews.droppedFrames.get(vaultId);
        if (dropped != null && hub.getBlockState(portalBlock).getBlock() instanceof VaultPortalBlock frame) {
            setFrameVaultId(hub, portalBlock, frame, null);
            previews.removeDropped(vaultId);
        }
    }

    private static void onServerTick(TickEvent.ServerTickEvent event) {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (event.phase != TickEvent.Phase.END || server.getTickCount() % CHECK_INTERVAL_TICKS != 0) {
            return;
        }
        Storage previews = getStorage(server);
        if (!startupChecked) {
            startupChecked = true;
            if (VaultPreviewRules.isDroppedAtStartup(VaultPreviewRules.getMode())) {
                List.copyOf(previews.previews.values()).forEach(preview -> drop(server, preview, "the server restarted"));
            }
        }
        for (Preview preview : List.copyOf(previews.previews.values())) {
            checkPreview(server, preview);
        }
        for (Preview dropped : List.copyOf(previews.droppedFrames.values())) {
            ServerLevel hub = server.getLevel(dropped.hubDimension());
            if (hub == null) {
                previews.removeDropped(dropped.vaultId());
            }
            else if (hub.isLoaded(dropped.frameBlock())) {
                resetDroppedFrame(hub, dropped);
            }
        }
    }

    private static void checkPreview(MinecraftServer server, Preview preview) {
        Vault vault = ServerVaults.get(preview.vaultId()).orElse(null);
        if (vault == null) {
            drop(server, preview, "its vault was closed by another mod or a command");
            return;
        }
        if (!vault.get(Vault.LISTENERS).getAll().isEmpty()) {
            getStorage(server).remove(preview.vaultId());
            LOGGER.info("Vault {} got its first runner; it is a normal vault from now on", preview.vaultId());
            return;
        }
        ServerLevel hub = server.getLevel(preview.hubDimension());
        if (hub == null) {
            drop(server, preview, "its hub dimension is gone");
        }
        else if (hub.isLoaded(preview.frameBlock()) && !isFrameOf(hub, preview.frameBlock(), preview.vaultId())) {
            drop(server, preview, "its frame was broken");
        }
        else if (VaultPreviewRules.isExpiredNow(preview.createdAt(), hub.getGameTime())) {
            drop(server, preview, "nobody entered before vaultPreviewTimeoutMinutes");
        }
    }

    // ends the unentered vault the way Vault Hunters ends one without listeners, and frees its frame
    private static void drop(MinecraftServer server, Preview preview, String reason) {
        Storage previews = getStorage(server);
        previews.remove(preview.vaultId());
        Vault vault = ServerVaults.get(preview.vaultId()).orElse(null);
        if (vault != null) {
            ServerVaults.getWorld(vault).ifPresent(VirtualWorld::markForDeletion);
            vault.set(Vault.FINISHED);
            vault.releaseServer();
        }
        ServerLevel hub = server.getLevel(preview.hubDimension());
        if (hub != null && hub.isLoaded(preview.frameBlock())) {
            resetDroppedFrame(hub, preview);
        }
        else if (hub != null) {
            previews.addDropped(preview);
        }
        LOGGER.info("Dropped the unentered vault {} of the frame at {} in {}: {}",
            preview.vaultId(), preview.frameBlock(), preview.hubDimension().location(), reason);
    }

    private static void resetDroppedFrame(ServerLevel hub, Preview dropped) {
        if (isFrameOf(hub, dropped.frameBlock(), dropped.vaultId())
            && hub.getBlockState(dropped.frameBlock()).getBlock() instanceof VaultPortalBlock frame) {
            setFrameVaultId(hub, dropped.frameBlock(), frame, null);
        }
        getStorage(hub.getServer()).removeDropped(dropped.vaultId());
    }

    private static void setFrameVaultId(ServerLevel hub, BlockPos portalBlock, VaultPortalBlock frame, @Nullable UUID vaultId) {
        frame.fill(hub, portalBlock, tile -> tile.getData().ifPresent(crystal -> {
            crystal.getProperties().setVaultId(vaultId);
            tile.setChanged();
        }), new HashSet<>());
    }

    private static boolean isFrameOf(ServerLevel hub, BlockPos portalBlock, UUID vaultId) {
        CrystalData crystal = getCrystal(hub, portalBlock);
        return crystal != null && vaultId.equals(crystal.getProperties().getVaultId());
    }

    @Nullable
    private static CrystalData getCrystal(Level level, BlockPos portalBlock) {
        BlockEntity blockEntity = level.getBlockEntity(portalBlock);
        return blockEntity instanceof VaultPortalTileEntity tile ? tile.getData().orElse(null) : null;
    }

    private static Storage getStorage(MinecraftServer server) {
        if (storage == null) {
            storage = server.overworld().getDataStorage().computeIfAbsent(Storage::load, Storage::new, DATA_NAME);
        }
        return storage;
    }

    private static final class Storage extends SavedData {
        private final Map<UUID, Preview> previews = new LinkedHashMap<>();
        // dropped previews whose frame chunk was unloaded, so the frame still names the deleted vault
        private final Map<UUID, Preview> droppedFrames = new LinkedHashMap<>();

        private void add(Preview preview) {
            previews.put(preview.vaultId(), preview);
            setDirty();
        }

        private void remove(UUID vaultId) {
            if (previews.remove(vaultId) != null) {
                setDirty();
            }
        }

        private void addDropped(Preview preview) {
            droppedFrames.put(preview.vaultId(), preview);
            setDirty();
        }

        private void removeDropped(UUID vaultId) {
            if (droppedFrames.remove(vaultId) != null) {
                setDirty();
            }
        }

        private static Storage load(CompoundTag tag) {
            Storage loaded = new Storage();
            readList(tag.getList("previews", Tag.TAG_COMPOUND), loaded.previews);
            readList(tag.getList("droppedFrames", Tag.TAG_COMPOUND), loaded.droppedFrames);
            return loaded;
        }

        @Override
        public CompoundTag save(CompoundTag tag) {
            tag.put("previews", writeList(previews));
            tag.put("droppedFrames", writeList(droppedFrames));
            return tag;
        }

        private static void readList(ListTag list, Map<UUID, Preview> into) {
            for (int index = 0; index < list.size(); index++) {
                CompoundTag entry = list.getCompound(index);
                Preview preview = new Preview(
                    entry.getUUID("vault"),
                    ResourceKey.create(Registry.DIMENSION_REGISTRY, new ResourceLocation(entry.getString("hub"))),
                    NbtUtils.readBlockPos(entry.getCompound("frame")),
                    entry.getLong("createdAt")
                );
                into.put(preview.vaultId(), preview);
            }
        }

        private static ListTag writeList(Map<UUID, Preview> previews) {
            ListTag list = new ListTag();
            for (Preview preview : previews.values()) {
                CompoundTag entry = new CompoundTag();
                entry.putUUID("vault", preview.vaultId());
                entry.putString("hub", preview.hubDimension().location().toString());
                entry.put("frame", NbtUtils.writeBlockPos(preview.frameBlock()));
                entry.putLong("createdAt", preview.createdAt());
                list.add(entry);
            }
            return list;
        }
    }
}
