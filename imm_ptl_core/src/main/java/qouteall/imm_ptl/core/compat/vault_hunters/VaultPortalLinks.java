package qouteall.imm_ptl.core.compat.vault_hunters;

import iskallia.vault.block.VaultPortalBlock;
import iskallia.vault.block.entity.VaultPortalTileEntity;
import iskallia.vault.core.vault.ClassicPortalLogic;
import iskallia.vault.core.vault.EntityState;
import iskallia.vault.core.vault.PortalData;
import iskallia.vault.core.vault.PortalLogic;
import iskallia.vault.core.vault.Vault;
import iskallia.vault.core.vault.WorldManager;
import iskallia.vault.core.vault.player.Listeners;
import iskallia.vault.core.world.storage.VirtualWorld;
import iskallia.vault.entity.entity.SpiritEntity;
import iskallia.vault.util.ServerScheduler;
import iskallia.vault.world.data.ServerVaults;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.NetherPortalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.server.ServerLifecycleHooks;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import qouteall.imm_ptl.core.McHelper;
import qouteall.imm_ptl.core.compat.mixin.IEVaultListenersLogic;
import qouteall.imm_ptl.core.compat.vault_hunters.VaultPortalGeometry.Opening;
import qouteall.imm_ptl.core.compat.vault_hunters.VaultPortalGeometry.Placement;
import qouteall.imm_ptl.core.compat.vault_hunters.VaultPortalGeometry.WindowSize;
import qouteall.imm_ptl.core.compat.vault_hunters.VaultRules.VaultPortalRole;
import qouteall.imm_ptl.core.portal.PortalDimensionFilter;

import javax.annotation.Nullable;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;

/**
 * See-through vault portals (V2/V3): once a hub frame has a vault, its opening gets a two-way window to the vault's
 * start-room portal. Each crossing runs Vault Hunters' own logic for the Vault Hunters portal block behind the window
 * (frame: join checks and the runner entry, start room: portal collide, i.e. bail and exit objectives) and only the
 * teleport is Immersive Portals': VH's touch is skipped while a player walks up to a window, its recall teleport on an
 * exit through the window is replaced by the crossing itself, and `onTeleport` runs once after an entry.
 */
public final class VaultPortalLinks {
    private static final Logger LOGGER = LogManager.getLogger();
    private static final int RESOLVE_INTERVAL_TICKS = 20;
    // the start room is generated when the first runner arrives; a link that cannot be made by then is dropped
    private static final long PENDING_LINK_TIMEOUT_TICKS = 20L * 120;
    // Vault Hunters frames are at most 21 x 21 blocks inside
    private static final int MAX_FRAME_BLOCKS = 21 * 21;
    private static final double LINK_SEARCH_RADIUS = 24;
    // a player whose widened box is at a window and who is still in front of it is about to cross it; Vault Hunters'
    // touch fires about 0.3 blocks before the eye reaches the plane, so the box is widened by that much
    private static final double APPROACH_MARGIN = 0.3;
    private static final double APPROACH_PLANE_DISTANCE = 1.5;
    // where the client says it crossed must be at the window and near the server's own position of the player, which
    // lags the client's by up to a tick of movement; the crossing point also decides where the player lands
    private static final double CROSSING_EYE_PLANE_DISTANCE = 1.0;
    private static final double CROSSING_EYE_LAG = 2.0;
    // Vault Hunters resends the experience bar this long after its own dimension changes
    private static final int EXPERIENCE_RESYNC_DELAY_TICKS = 20;

    private record PendingLink(ResourceKey<Level> hubDimension, BlockPos touchedBlock, Vec3 touchPosition, long requestedAt) {
    }

    private enum LinkAttempt {
        LINKED,
        WAITING,
        IMPOSSIBLE
    }

    private static final Map<UUID, PendingLink> pendingLinks = new HashMap<>();
    // vaults whose frame or start room cannot carry a window (floor frames, odd templates): Vault Hunters' portal stays
    private static final Set<UUID> unlinkableVaults = new HashSet<>();
    private static final WeakHashMap<ServerPlayer, Integer> lastWindowEntryTick = new WeakHashMap<>();
    private static final WeakHashMap<ServerPlayer, Integer> lastInFrontOfWindowTick = new WeakHashMap<>();
    // the player whose crossing is running Vault Hunters' portal logic right now (server thread only)
    @Nullable
    private static ServerPlayer crossingPlayer;

    private VaultPortalLinks() {
    }

    static void init() {
        MinecraftForge.EVENT_BUS.addListener(VaultPortalLinks::onServerTick);
        MinecraftForge.EVENT_BUS.addListener((ServerStoppedEvent event) -> {
            pendingLinks.clear();
            unlinkableVaults.clear();
            lastWindowEntryTick.clear();
            lastInFrontOfWindowTick.clear();
            crossingPlayer = null;
        });
    }

    public static boolean isCrossing(Entity entity) {
        return crossingPlayer != null && entity == crossingPlayer;
    }

    /**
     * A player walking up to a window of this portal's vault is handled by the window's crossing, so Vault Hunters'
     * touch must not join or bail them first. A player who stays behind the window plane without crossing (the client
     * did not cross, or paused after a refusal) is left to Vault Hunters' own touch again, see
     * {@link VaultRules#isTouchLeftToWindow}.
     */
    public static boolean isTouchLeftToWindow(Entity entity, Level level, BlockPos pos) {
        if (!(entity instanceof ServerPlayer player) || isCrossing(player)) {
            return false;
        }
        UUID vaultId = getPortalVaultId(level, pos);
        if (vaultId == null) {
            return false;
        }
        AABB approach = player.getBoundingBox().inflate(APPROACH_MARGIN);
        Vec3 eye = player.getEyePosition();
        Vec3 previousEye = new Vec3(player.xo, player.yo + player.getEyeHeight(), player.zo);
        boolean isInFrontOfWindow = !level.getEntitiesOfClass(VaultPortal.class, approach.inflate(APPROACH_PLANE_DISTANCE),
            portal -> vaultId.equals(portal.getVaultId()) && portal.isBoxAtWindow(approach, APPROACH_PLANE_DISTANCE)
                && (portal.isInFrontOfPortal(eye) || portal.isInFrontOfPortal(previousEye))
        ).isEmpty();
        if (isInFrontOfWindow) {
            lastInFrontOfWindowTick.put(player, player.server.getTickCount());
        }
        return VaultRules.isTouchLeftToWindow(isInFrontOfWindow, getTicksSince(lastInFrontOfWindowTick, player),
            ServerVaults.get(level).isPresent(), getTicksSince(lastWindowEntryTick, player));
    }

    public static void onPortalBlockTouched(ServerLevel level, BlockPos pos, ServerPlayer player) {
        if (isCrossing(player) || ServerVaults.get(level).isPresent()) {
            return;
        }
        UUID vaultId = getVaultId(level, pos);
        // windows of a chunk whose entities are not loaded yet would not be found, and be made a second time
        if (vaultId == null || pendingLinks.containsKey(vaultId) || unlinkableVaults.contains(vaultId)
            || !level.areEntitiesLoaded(ChunkPos.asLong(pos)) || ServerVaults.get(vaultId).isEmpty()
            || hasWindow(level, pos, vaultId, VaultPortalRole.ENTRY)) {
            return;
        }
        // the previous tick's position: a fast player can already be past the frame's plane when the touch fires
        Vec3 touchPosition = new Vec3(player.xo, player.yo, player.zo);
        pendingLinks.put(vaultId, new PendingLink(level.dimension(), pos.immutable(), touchPosition, level.getGameTime()));
    }

    public static void removeIfStale(VaultPortal portal) {
        boolean vaultExists = ServerVaults.get(portal.getVaultId()).isPresent();
        boolean frameIntact = portal.getRole() != VaultPortalRole.ENTRY
            || portal.getVaultId().equals(getVaultId(portal.level, portal.getVaultHuntersBlock()));
        boolean isTurnedOff = PortalDimensionFilter.isPortalDisabled(portal.level.dimension(), portal.dimensionTo);
        if (!vaultExists || !frameIntact || isTurnedOff) {
            LOGGER.info("Removed the {} window of vault {} at {}: {}", portal.getRole(), portal.getVaultId(),
                portal.getVaultHuntersBlock(), !vaultExists ? "the vault is gone"
                    : !frameIntact ? "the frame is gone" : "portals are turned off there (portalDisabledDimensions)");
            portal.remove(Entity.RemovalReason.KILLED);
        }
    }

    public static boolean admitCrossing(ServerPlayer player, VaultPortal portal, Vec3 crossingEyePos) {
        Vault vault = ServerVaults.get(portal.getVaultId()).orElse(null);
        BlockPos block = portal.getVaultHuntersBlock();
        BlockState state = portal.level.getBlockState(block);
        boolean isAtWindow = portal.isPointInPortalProjection(crossingEyePos)
            && Math.abs(portal.getDistanceToPlane(crossingEyePos)) < CROSSING_EYE_PLANE_DISTANCE
            && crossingEyePos.distanceTo(player.getEyePosition()) < CROSSING_EYE_LAG;
        if (vault == null || player.level != portal.level || !(state.getBlock() instanceof VaultPortalBlock) || !isAtWindow) {
            return logCrossing(false, player, portal, "the player is not at the window");
        }
        if (portal.getRole() == VaultPortalRole.EXIT && VaultRules.isExitTooSoonAfterEntry(getTicksSince(lastWindowEntryTick, player))) {
            return logCrossing(false, player, portal, "the player has only just come in");
        }
        boolean wasListener = isListener(vault, player);
        crossingPlayer = player;
        try {
            state.entityInside(portal.level, block, player);
        }
        finally {
            crossingPlayer = null;
        }
        boolean admitted = VaultRules.isVaultPortalCrossingAdmitted(portal.getRole(), wasListener, isListener(vault, player));
        if (admitted && portal.getRole() == VaultPortalRole.ENTRY) {
            lastWindowEntryTick.put(player, player.server.getTickCount());
        }
        return logCrossing(admitted, player, portal, admitted ? "Vault Hunters let them through" : "Vault Hunters said no");
    }

    public static void onCrossed(ServerPlayer player, VaultPortal portal) {
        ServerScheduler.INSTANCE.schedule(EXPERIENCE_RESYNC_DELAY_TICKS, () -> player.giveExperiencePoints(0));
        if (portal.getRole() != VaultPortalRole.ENTRY) {
            return;
        }
        Vault vault = ServerVaults.get(portal.getVaultId()).orElse(null);
        VirtualWorld world = vault == null ? null : ServerVaults.getWorld(vault).orElse(null);
        if (world == null || player.level != world) {
            throw new IllegalStateException("%s crossed into vault %s, but is in %s"
                .formatted(player.getName().getString(), portal.getVaultId(), player.level.dimension().location()));
        }
        // Vault Hunters runs this right after its own entry teleport; its keepInVault skips players already inside
        vault.get(Vault.LISTENERS).ifPresent(Listeners.LOGIC,
            logic -> ((IEVaultListenersLogic) logic).ip_onTeleport(world, vault, player));
    }

    /**
     * Vault Hunters' exit recalls the runner to where they joined; for a crossing through the start-room window the
     * crossing itself is the way back, so only the rest of the recall happens: a carried spirit goes out, the join
     * game mode is restored.
     *
     * @return whether the recall teleport was replaced
     */
    public static boolean replaceRecallTeleport(EntityState state, Entity entity) {
        if (!isCrossing(entity) || !(entity instanceof ServerPlayer player)) {
            return false;
        }
        if (!player.getPassengers().isEmpty() && player.getPassengers().get(0) instanceof SpiritEntity spirit) {
            spirit.teleportOut();
        }
        state.ifPresent(EntityState.GAME_MODE, player::setGameMode);
        return true;
    }

    private static long getTicksSince(Map<ServerPlayer, Integer> ticks, ServerPlayer player) {
        Integer tick = ticks.get(player);
        return tick == null ? Long.MAX_VALUE : player.server.getTickCount() - tick;
    }

    private static boolean logCrossing(boolean admitted, ServerPlayer player, VaultPortal portal, String reason) {
        LOGGER.info("{} the {} crossing of {} through the window of vault {}: {}", admitted ? "Admitted" : "Refused",
            portal.getRole(), player.getName().getString(), portal.getVaultId(), reason);
        return admitted;
    }

    private static void onServerTick(TickEvent.ServerTickEvent event) {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (event.phase != TickEvent.Phase.END || server.getTickCount() % RESOLVE_INTERVAL_TICKS != 0) {
            return;
        }
        unlinkableVaults.removeIf(vaultId -> ServerVaults.get(vaultId).isEmpty());
        Iterator<Map.Entry<UUID, PendingLink>> iterator = pendingLinks.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, PendingLink> entry = iterator.next();
            UUID vaultId = entry.getKey();
            PendingLink link = entry.getValue();
            ServerLevel hub = server.getLevel(link.hubDimension());
            Vault vault = ServerVaults.get(vaultId).orElse(null);
            if (hub == null || vault == null) {
                iterator.remove();
                continue;
            }
            LinkAttempt attempt = tryLink(hub, vault, vaultId, link);
            if (attempt == LinkAttempt.IMPOSSIBLE) {
                unlinkableVaults.add(vaultId);
            }
            boolean timedOut = hub.getGameTime() - link.requestedAt() > PENDING_LINK_TIMEOUT_TICKS;
            if (attempt != LinkAttempt.WAITING || timedOut) {
                iterator.remove();
            }
        }
    }

    private static LinkAttempt tryLink(ServerLevel hub, Vault vault, UUID vaultId, PendingLink link) {
        VirtualWorld world = ServerVaults.getWorld(vault).orElse(null);
        WorldManager worldManager = vault.getOptional(Vault.WORLD).orElse(null);
        if (world == null || worldManager == null || !hub.isPositionEntityTicking(link.touchedBlock())) {
            return LinkAttempt.WAITING;
        }
        if (PortalDimensionFilter.isPortalDisabled(hub.dimension(), world.dimension())) {
            return impossible(vaultId, "portals are turned off there (portalDisabledDimensions)");
        }
        PortalLogic portalLogic = worldManager.getOptional(WorldManager.PORTAL_LOGIC).orElse(null);
        if (!(portalLogic instanceof ClassicPortalLogic classicPortalLogic)) {
            return impossible(vaultId, "its portal logic is not Vault Hunters' classic one");
        }
        PortalData entrance = classicPortalLogic.getPortals(ClassicPortalLogic.ENTRANCE).findFirst().orElse(null);
        if (entrance == null || !world.isPositionEntityTicking(entrance.get(PortalData.MIN))) {
            return LinkAttempt.WAITING;
        }

        Opening entranceOpening = getEntranceOpening(entrance);
        Direction arrival = entranceOpening == null ? null : getArrivalDirection(world, entranceOpening, worldManager.get(WorldManager.FACING));
        BlockState frameState = hub.getBlockState(link.touchedBlock());
        if (entranceOpening == null || arrival == null) {
            return impossible(vaultId, "its start-room portal %s..%s is not a flat wall portal with one open side"
                .formatted(entrance.get(PortalData.MIN), entrance.get(PortalData.MAX)));
        }
        if (!(frameState.getBlock() instanceof VaultPortalBlock) || frameState.getValue(VaultPortalBlock.HORIZONTAL)) {
            return impossible(vaultId, "its hub frame is a floor frame or gone");
        }
        Opening frameOpening = getFrameOpening(hub, link.touchedBlock(), vaultId, frameState);
        if (frameOpening == null) {
            return impossible(vaultId, "its hub frame at %s is not a flat rectangle".formatted(link.touchedBlock()));
        }
        BlockPos frameBlock = getBottomCentreBlock(frameOpening);
        BlockPos entranceBlock = getBottomCentreBlock(entranceOpening);
        if (hasWindow(hub, frameBlock, vaultId, VaultPortalRole.ENTRY) || hasWindow(world, entranceBlock, vaultId, VaultPortalRole.EXIT)) {
            return LinkAttempt.LINKED;
        }

        Direction creatorSide = getSideOf(frameOpening, link.touchPosition());
        WindowSize fittedEntry = VaultPortalGeometry.fitEntryWindow(frameOpening, entranceOpening, (column, row) -> isFree(
            world, VaultPortalGeometry.getRoomCell(frameOpening, entranceOpening, arrival, column, row)
        ));
        for (Direction viewerSide : new Direction[]{creatorSide, creatorSide.getOpposite()}) {
            Placement entry = fittedEntry == null
                ? VaultPortalGeometry.place(frameOpening, viewerSide, entranceOpening, arrival)
                : VaultPortalGeometry.placeFittedEntry(frameOpening, viewerSide, entranceOpening, arrival, fittedEntry);
            McHelper.spawnServerEntity(VaultPortal.create(hub, VaultPortalRole.ENTRY, vaultId, frameBlock, entry, world.dimension()));
        }
        Placement exit = VaultPortalGeometry.place(entranceOpening, arrival, frameOpening, creatorSide);
        McHelper.spawnServerEntity(VaultPortal.create(world, VaultPortalRole.EXIT, vaultId, entranceBlock, exit, hub.dimension()));
        LOGGER.info("Linked the hub frame {}..{} in {} to the start-room portal {}..{} of vault {} (entry window {})",
            frameOpening.min(), frameOpening.max(), hub.dimension().location(),
            entranceOpening.min(), entranceOpening.max(), vaultId,
            fittedEntry == null ? "over the smaller opening" : fittedEntry.width() + " x " + fittedEntry.height());
        return LinkAttempt.LINKED;
    }

    private static LinkAttempt impossible(UUID vaultId, String reason) {
        LOGGER.info("Vault {} keeps the Vault Hunters portal without a window: {}", vaultId, reason);
        return LinkAttempt.IMPOSSIBLE;
    }

    // a wall portal one block thick along X or Z (not both, which would be a single column)
    @Nullable
    private static Opening getEntranceOpening(PortalData entrance) {
        BlockPos min = entrance.get(PortalData.MIN);
        BlockPos max = entrance.get(PortalData.MAX);
        boolean isThinAlongX = getThickness(min, max, Direction.Axis.X) == 1;
        boolean isThinAlongZ = getThickness(min, max, Direction.Axis.Z) == 1;
        if (isThinAlongX == isThinAlongZ || getThickness(min, max, Direction.Axis.Y) < 2) {
            return null;
        }
        return new Opening(min, max, isThinAlongZ ? Direction.Axis.Z : Direction.Axis.X);
    }

    @Nullable
    private static Direction getArrivalDirection(ServerLevel world, Opening entrance, Direction vaultFacing) {
        BlockPos bottom = getBottomCentreBlock(entrance);
        Direction positive = Direction.fromAxisAndDirection(entrance.normalAxis(), Direction.AxisDirection.POSITIVE);
        return VaultPortalGeometry.getArrivalDirection(vaultFacing, entrance.normalAxis(),
            isWalkable(world, bottom.relative(positive)), isWalkable(world, bottom.relative(positive.getOpposite())));
    }

    private static boolean isWalkable(ServerLevel world, BlockPos feet) {
        return isFree(world, feet) && isFree(world, feet.above());
    }

    private static boolean isFree(ServerLevel world, BlockPos pos) {
        return world.getBlockState(pos).getCollisionShape(world, pos).isEmpty();
    }

    // the connected portal blocks of this vault around the touched one; the frame blocks' axis is the plane's width
    @Nullable
    private static Opening getFrameOpening(ServerLevel hub, BlockPos touchedBlock, UUID vaultId, BlockState frameState) {
        Direction.Axis widthAxis = frameState.getValue(NetherPortalBlock.AXIS);
        Direction.Axis normalAxis = widthAxis == Direction.Axis.X ? Direction.Axis.Z : Direction.Axis.X;
        Set<BlockPos> blocks = collectFrameBlocks(hub, touchedBlock, vaultId);
        if (blocks == null) {
            return null;
        }
        BlockPos min = blocks.stream().reduce(touchedBlock, VaultPortalLinks::minCorner);
        BlockPos max = blocks.stream().reduce(touchedBlock, VaultPortalLinks::maxCorner);
        long boxVolume = (long) (max.getX() - min.getX() + 1) * (max.getY() - min.getY() + 1) * (max.getZ() - min.getZ() + 1);
        if (getThickness(min, max, normalAxis) != 1 || boxVolume != blocks.size()) {
            return null;
        }
        return new Opening(min, max, normalAxis);
    }

    @Nullable
    private static Set<BlockPos> collectFrameBlocks(ServerLevel hub, BlockPos start, UUID vaultId) {
        Set<BlockPos> found = new HashSet<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        queue.add(start.immutable());
        found.add(start.immutable());
        while (!queue.isEmpty()) {
            BlockPos current = queue.poll();
            for (Direction direction : Direction.values()) {
                BlockPos next = current.relative(direction);
                if (!found.contains(next) && vaultId.equals(getVaultId(hub, next))) {
                    if (found.size() >= MAX_FRAME_BLOCKS) {
                        return null;
                    }
                    found.add(next);
                    queue.add(next);
                }
            }
        }
        return found;
    }

    private static int getThickness(BlockPos min, BlockPos max, Direction.Axis axis) {
        return max.get(axis) - min.get(axis) + 1;
    }

    private static BlockPos minCorner(BlockPos a, BlockPos b) {
        return new BlockPos(Math.min(a.getX(), b.getX()), Math.min(a.getY(), b.getY()), Math.min(a.getZ(), b.getZ()));
    }

    private static BlockPos maxCorner(BlockPos a, BlockPos b) {
        return new BlockPos(Math.max(a.getX(), b.getX()), Math.max(a.getY(), b.getY()), Math.max(a.getZ(), b.getZ()));
    }

    private static Direction getSideOf(Opening opening, Vec3 position) {
        double plane = opening.bottomCentre().get(opening.normalAxis());
        Direction.AxisDirection axisDirection = position.get(opening.normalAxis()) >= plane
            ? Direction.AxisDirection.POSITIVE : Direction.AxisDirection.NEGATIVE;
        return Direction.fromAxisAndDirection(opening.normalAxis(), axisDirection);
    }

    private static BlockPos getBottomCentreBlock(Opening opening) {
        Vec3 bottomCentre = opening.bottomCentre();
        return new BlockPos(Math.floor(bottomCentre.x), opening.min().getY(), Math.floor(bottomCentre.z));
    }

    private static boolean hasWindow(Level level, BlockPos pos, UUID vaultId, VaultPortalRole role) {
        return !level.getEntitiesOfClass(VaultPortal.class, new AABB(pos).inflate(LINK_SEARCH_RADIUS),
            portal -> portal.getRole() == role && vaultId.equals(portal.getVaultId())).isEmpty();
    }

    // Vault Hunters' portals inside a vault carry no crystal; their vault is the level's
    @Nullable
    private static UUID getPortalVaultId(Level level, BlockPos pos) {
        return ServerVaults.get(level).map(vault -> vault.get(Vault.ID)).orElseGet(() -> getVaultId(level, pos));
    }

    @Nullable
    private static UUID getVaultId(Level level, BlockPos pos) {
        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (!(blockEntity instanceof VaultPortalTileEntity tile)) {
            return null;
        }
        return tile.getData().map(crystal -> crystal.getProperties().getVaultId()).orElse(null);
    }

    private static boolean isListener(Vault vault, ServerPlayer player) {
        return vault.getOptional(Vault.LISTENERS)
            .map(listeners -> listeners.contains(player.getUUID()))
            .orElse(false);
    }
}
