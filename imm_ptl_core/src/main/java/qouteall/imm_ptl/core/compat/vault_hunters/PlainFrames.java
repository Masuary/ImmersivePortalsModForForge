package qouteall.imm_ptl.core.compat.vault_hunters;

import iskallia.vault.block.VaultPortalBlock;
import iskallia.vault.event.ActiveFlags;
import iskallia.vault.world.data.ServerVaults;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Registry;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.TagsUpdatedEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.event.world.BlockEvent;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.server.ServerLifecycleHooks;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import qouteall.imm_ptl.core.compat.vault_hunters.PlainFrameRules.ToggleItem;
import qouteall.imm_ptl.core.compat.vault_hunters.VaultPortalGeometry.Opening;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Plain frames (owner 2026-11-08): sneaking and using the toggle item ({@link PlainFrameRules}) on a lit hub frame
 * switches its vault window off for everyone, so builds keep Vault Hunters' own portal texture and players enter and
 * leave through Vault Hunters' own teleport; the same use switches it back on. Only a player who could break the frame
 * may switch it. The flag belongs to the frame, not to one lighting: it stays through every vault run and relighting
 * (Vault Hunters puts a frame out when its vault ends) and goes when a block of the frame itself is broken (owner
 * 2026-11-08). Saved with the overworld.
 */
public final class PlainFrames {
    private static final Logger LOGGER = LogManager.getLogger();
    private static final String DATA_NAME = "imm_ptl_plain_vault_frames";
    private static final int CHECK_INTERVAL_TICKS = 20;

    private record PlainFrame(ResourceKey<Level> hubDimension, BlockPos min, BlockPos max, Direction.Axis normalAxis) {
        private boolean contains(ResourceKey<Level> dimension, BlockPos pos) {
            return hubDimension.equals(dimension) && PlainFrameRules.contains(min, max, pos);
        }
    }

    // loaded before the first tick: exit windows ask isVaultPlain from vault levels ticking on Vault Hunters' pool
    @Nullable
    private static volatile Storage storage;
    // touched from packet handling on the server thread only
    private static final Map<ServerPlayer, Integer> lastToggleAttemptTick = new WeakHashMap<>();

    private PlainFrames() {
    }

    static void init() {
        MinecraftForge.EVENT_BUS.addListener(PlainFrames::onRightClickBlock);
        MinecraftForge.EVENT_BUS.addListener(PlainFrames::onRightClickItem);
        MinecraftForge.EVENT_BUS.addListener(PlainFrames::onServerTick);
        MinecraftForge.EVENT_BUS.addListener(PlainFrames::onTagsUpdated);
        MinecraftForge.EVENT_BUS.addListener((ServerStartedEvent event) -> storage = loadStorage(event.getServer()));
        MinecraftForge.EVENT_BUS.addListener((ServerStoppedEvent event) -> {
            storage = null;
            lastToggleAttemptTick.clear();
        });
    }

    /**
     * Whether the hub frame whose opening holds {@code portalBlock} is plain: it gets no window and lighting it makes
     * no preview. Server thread only.
     */
    public static boolean isPlain(Level hub, BlockPos portalBlock) {
        Storage loaded = storage;
        return loaded != null && loaded.findFrame(hub.dimension(), portalBlock) != null;
    }

    /**
     * Whether this vault's windows were taken down because its frame was made plain; its exit window inside the vault
     * goes on its next check. Safe from any thread.
     */
    public static boolean isVaultPlain(UUID vaultId) {
        Storage loaded = storage;
        return loaded != null && loaded.plainVaults.contains(vaultId);
    }

    private static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (!(event.getPlayer() instanceof ServerPlayer player) || player instanceof FakePlayer
            || !(event.getWorld() instanceof ServerLevel hub) || !player.isSecondaryUseActive()) {
            return;
        }
        ToggleItem toggleItem = PlainFrameRules.getToggleItem();
        InteractionHand toggleHand = PlainFrameRules.getToggleHand(
            isToggleItem(player.getMainHandItem(), toggleItem), isToggleItem(player.getOffhandItem(), toggleItem));
        BlockPos pos = event.getPos();
        BlockState state = hub.getBlockState(pos);
        if (toggleHand == null || !(state.getBlock() instanceof VaultPortalBlock) || VaultPortalLinks.isFloorFrame(state)
            || ServerVaults.get(hub).isPresent()) {
            return;
        }
        if (PlainFrameRules.isOffHandUseAfterToggle(event.getHand(), toggleHand)) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.FAIL);
            // the client already placed or used its off-hand item for itself
            player.inventoryMenu.sendAllDataToRemote();
            return;
        }
        if (event.getHand() != toggleHand) {
            return;
        }
        event.setCanceled(true);
        // the client is not told and repeats a held use key every 4 ticks: every attempt restarts the cooldown, so a
        // held key switches the frame once
        Integer lastAttemptTick = lastToggleAttemptTick.put(player, player.server.getTickCount());
        if (lastAttemptTick != null && PlainFrameRules.isToggleCoolingDown(player.server.getTickCount() - lastAttemptTick)) {
            event.setCancellationResult(InteractionResult.FAIL);
            return;
        }
        event.setCancellationResult(toggle(hub, pos, state, player) ? InteractionResult.SUCCESS : InteractionResult.FAIL);
        // the client placed or used the item for itself (a block item, food)
        player.inventoryMenu.sendAllDataToRemote();
    }

    // the client follows a use on a block with a use of the held items in the air; after a switch those belong to it
    private static void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        if (!(event.getPlayer() instanceof ServerPlayer player)) {
            return;
        }
        Integer lastAttemptTick = lastToggleAttemptTick.get(player);
        if (lastAttemptTick != null && PlainFrameRules.isItemUseOfToggle(player.server.getTickCount() - lastAttemptTick)) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.FAIL);
            player.inventoryMenu.sendAllDataToRemote();
        }
    }

    private static boolean isToggleItem(ItemStack stack, ToggleItem toggleItem) {
        if (stack.isEmpty()) {
            return false;
        }
        return switch (toggleItem.kind()) {
            case NONE -> false;
            case ITEM -> toggleItem.id().equals(ForgeRegistries.ITEMS.getKey(stack.getItem()));
            case TAG -> stack.is(TagKey.create(Registry.ITEM_REGISTRY, toggleItem.id()));
        };
    }

    private static boolean toggle(ServerLevel hub, BlockPos portalBlock, BlockState state, ServerPlayer player) {
        Opening opening = VaultPortalLinks.getLitFrameOpening(hub, portalBlock, state);
        if (opening == null) {
            LOGGER.info("{} could not switch the vault window of the frame at {} in {}: its portal is not a flat rectangle",
                player.getName().getString(), portalBlock, hub.dimension().location());
            return false;
        }
        if (!mayBreak(player, hub, portalBlock, state)) {
            LOGGER.info("{} may not switch the vault window of the hub frame {}..{} in {}: they could not break it",
                player.getName().getString(), opening.min(), opening.max(), hub.dimension().location());
            player.displayClientMessage(new TextComponent("You may not change this frame").withStyle(ChatFormatting.RED), true);
            return false;
        }
        Storage frames = getStorage();
        PlainFrame plainFrame = frames.findFrame(hub.dimension(), portalBlock);
        UUID vaultId = VaultPortalLinks.getVaultId(hub, portalBlock);
        if (plainFrame == null) {
            frames.addFrame(new PlainFrame(hub.dimension(), opening.min(), opening.max(), opening.normalAxis()));
            if (vaultId != null) {
                frames.addVault(vaultId);
                VaultPortalLinks.removeEntryWindows(hub, opening, vaultId);
            }
            player.displayClientMessage(new TextComponent("Vault window off"), true);
        }
        else {
            frames.removeFrame(plainFrame);
            if (vaultId != null) {
                frames.removeVault(vaultId);
                if (ServerVaults.get(vaultId).isPresent()) {
                    VaultPortalLinks.requestLink(hub, portalBlock, player.position(), vaultId);
                }
            }
            player.displayClientMessage(new TextComponent("Vault window on"), true);
        }
        LOGGER.info("{} switched the vault window of the hub frame {}..{} in {} {} (vault {})", player.getName().getString(),
            opening.min(), opening.max(), hub.dimension().location(), plainFrame == null ? "off" : "on", vaultId);
        return true;
    }

    /**
     * The checks breaking the block would get, without breaking it: spectators, spawn protection and the world border,
     * adventure mode, then Forge's break event posted as a query, which claim and protection mods cancel. Vault
     * Hunters' vein miner would really dig the block for the query; its own flag for a running area dig keeps it out.
     */
    private static boolean mayBreak(ServerPlayer player, ServerLevel level, BlockPos pos, BlockState state) {
        if (player.isSpectator() || !level.mayInteract(player, pos)
            || player.blockActionRestricted(level, pos, player.gameMode.getGameModeForPlayer())) {
            return false;
        }
        BlockEvent.BreakEvent breakQuery = new BlockEvent.BreakEvent(level, pos, state, player);
        ActiveFlags.IS_AOE_MINING.push();
        try {
            MinecraftForge.EVENT_BUS.post(breakQuery);
        }
        finally {
            ActiveFlags.IS_AOE_MINING.pop();
        }
        return !breakQuery.isCanceled();
    }

    private static void onServerTick(TickEvent.ServerTickEvent event) {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        Storage frames = storage;
        if (event.phase != TickEvent.Phase.END || frames == null || server.getTickCount() % CHECK_INTERVAL_TICKS != 0) {
            return;
        }
        for (PlainFrame frame : List.copyOf(frames.frames)) {
            ServerLevel hub = server.getLevel(frame.hubDimension());
            if (hub == null) {
                drop(frames, frame, "its dimension is gone");
            }
            else if (isFrameBroken(hub, frame)) {
                drop(frames, frame, "its frame was broken");
            }
        }
        List<UUID> endedVaults = frames.plainVaults.stream().filter(vaultId -> ServerVaults.get(vaultId).isEmpty()).toList();
        endedVaults.forEach(frames::removeVault);
    }

    private static boolean isFrameBroken(ServerLevel hub, PlainFrame frame) {
        List<BlockPos> ring = PlainFrameRules.getFrameRing(frame.min(), frame.max(), frame.normalAxis());
        if (!ring.stream().allMatch(hub::isLoaded)) {
            return false;
        }
        return !ring.stream().allMatch(pos -> VaultPortalBlock.FRAME.test(hub.getBlockState(pos), hub, pos));
    }

    private static void drop(Storage frames, PlainFrame frame, String reason) {
        frames.removeFrame(frame);
        LOGGER.info("The hub frame {}..{} in {} gets vault windows again: {}",
            frame.min(), frame.max(), frame.hubDimension().location(), reason);
    }

    // an item tag can only be checked once tags are loaded, after the config
    private static void onTagsUpdated(TagsUpdatedEvent event) {
        ToggleItem toggleItem = PlainFrameRules.getToggleItem();
        if (event.getUpdateCause() != TagsUpdatedEvent.UpdateCause.SERVER_DATA_LOAD
            || toggleItem.kind() != PlainFrameRules.ToggleItemKind.TAG) {
            return;
        }
        boolean hasItems = event.getTagManager().registryOrThrow(Registry.ITEM_REGISTRY)
            .getTag(TagKey.create(Registry.ITEM_REGISTRY, toggleItem.id()))
            .map(tag -> tag.size() > 0)
            .orElse(false);
        if (!hasItems) {
            LOGGER.warn("vaultWindowToggleItem names the item tag #{}, which has no items: no item switches vault windows",
                toggleItem.id());
        }
    }

    private static Storage getStorage() {
        Storage loaded = storage;
        if (loaded == null) {
            throw new IllegalStateException("Plain vault frames were used before the server started");
        }
        return loaded;
    }

    private static Storage loadStorage(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(Storage::load, Storage::new, DATA_NAME);
    }

    private static final class Storage extends SavedData {
        // changed on the server thread only
        private final List<PlainFrame> frames = new ArrayList<>();
        private final Set<UUID> plainVaults = ConcurrentHashMap.newKeySet();

        @Nullable
        private PlainFrame findFrame(ResourceKey<Level> dimension, BlockPos portalBlock) {
            for (PlainFrame frame : frames) {
                if (frame.contains(dimension, portalBlock)) {
                    return frame;
                }
            }
            return null;
        }

        private void addFrame(PlainFrame frame) {
            frames.add(frame);
            setDirty();
        }

        private void removeFrame(PlainFrame frame) {
            if (frames.remove(frame)) {
                setDirty();
            }
        }

        private void addVault(UUID vaultId) {
            if (plainVaults.add(vaultId)) {
                setDirty();
            }
        }

        private void removeVault(UUID vaultId) {
            if (plainVaults.remove(vaultId)) {
                setDirty();
            }
        }

        private static Storage load(CompoundTag tag) {
            Storage loaded = new Storage();
            ListTag frameList = tag.getList("frames", Tag.TAG_COMPOUND);
            for (int index = 0; index < frameList.size(); index++) {
                CompoundTag entry = frameList.getCompound(index);
                Direction.Axis normalAxis = Direction.Axis.byName(entry.getString("normalAxis"));
                if (normalAxis == null) {
                    throw new IllegalStateException("Saved plain vault frame without an axis: " + entry);
                }
                loaded.frames.add(new PlainFrame(
                    ResourceKey.create(Registry.DIMENSION_REGISTRY, new ResourceLocation(entry.getString("hub"))),
                    NbtUtils.readBlockPos(entry.getCompound("min")),
                    NbtUtils.readBlockPos(entry.getCompound("max")),
                    normalAxis
                ));
            }
            ListTag vaultList = tag.getList("vaults", Tag.TAG_INT_ARRAY);
            for (Tag vault : vaultList) {
                loaded.plainVaults.add(NbtUtils.loadUUID(vault));
            }
            return loaded;
        }

        @Override
        public CompoundTag save(CompoundTag tag) {
            ListTag frameList = new ListTag();
            for (PlainFrame frame : frames) {
                CompoundTag entry = new CompoundTag();
                entry.putString("hub", frame.hubDimension().location().toString());
                entry.put("min", NbtUtils.writeBlockPos(frame.min()));
                entry.put("max", NbtUtils.writeBlockPos(frame.max()));
                entry.putString("normalAxis", frame.normalAxis().getName());
                frameList.add(entry);
            }
            ListTag vaultList = new ListTag();
            for (UUID vaultId : plainVaults) {
                vaultList.add(NbtUtils.createUUID(vaultId));
            }
            tag.put("frames", frameList);
            tag.put("vaults", vaultList);
            return tag;
        }
    }
}
