package qouteall.imm_ptl.core.chunk_loading;

import it.unimi.dsi.fastutil.longs.*;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundForgetLevelChunkPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import org.apache.commons.lang3.Validate;
import org.jetbrains.annotations.Nullable;
import qouteall.imm_ptl.core.IPGlobal;
import qouteall.imm_ptl.core.McHelper;
import qouteall.imm_ptl.core.miscellaneous.GcMonitor;
import qouteall.imm_ptl.core.network.PacketRedirection;
import qouteall.q_misc_util.Helper;
import qouteall.q_misc_util.MiscHelper;
import qouteall.q_misc_util.my_util.SignalBiArged;

import java.lang.ref.WeakReference;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Stream;

public class NewChunkTrackingGraph {
    
    public static final int updateInterval = 40;
    
    public static class PlayerWatchRecord {
        public final ServerPlayer player;
        public final ResourceKey<Level> dimension;
        public final long chunkPos;
        public long lastWatchTime;
        public int distanceToSource;
        public boolean isDirectLoading;
        public boolean isLoadedToPlayer;
        public boolean isValid = true;
        
        public PlayerWatchRecord(
            ServerPlayer player, ResourceKey<Level> dimension,
            long chunkPos, long lastWatchTime,
            int distanceToSource, boolean isDirectLoading, boolean isLoadedToPlayer
        ) {
            this.player = player;
            this.dimension = dimension;
            this.chunkPos = chunkPos;
            this.lastWatchTime = lastWatchTime;
            this.distanceToSource = distanceToSource;
            this.isDirectLoading = isDirectLoading;
            this.isLoadedToPlayer = isLoadedToPlayer;
        }
        
        @Override
        public String toString() {
            return String.format(
                "%s (%d,%d) distance:%d valid:%s loaded:%s",
                dimension.location(),
                ChunkPos.getX(chunkPos),
                ChunkPos.getZ(chunkPos),
                distanceToSource,
                isValid,
                isLoadedToPlayer
            );
        }
    }
    
    private static void removeInactiveWatchers(
        ArrayList<PlayerWatchRecord> records,
        Predicate<PlayerWatchRecord> predicate,
        Consumer<PlayerWatchRecord> informer
    ) {
        records.removeIf(r -> {
            Validate.isTrue(r.isValid);
            
            boolean shouldRemove = predicate.test(r);
            if (shouldRemove) {
                informer.accept(r);
                r.isValid = false;
            }
            return shouldRemove;
        });
    }
    
    private static boolean shouldAddCustomTicket(
        ServerLevel world,
        long chunkPos,
        ArrayList<PlayerWatchRecord> records
    ) {
        return Helper.indexOf(records, r ->
            (r.isLoadedToPlayer) && (!r.isDirectLoading)
        ) != -1;
    }
    
    // Every chunk has a list of watching records. Written on the server thread outside level ticks only; read from
    // levels that tick on other threads (see LevelThreads).
    private static final Map<ResourceKey<Level>, Long2ObjectLinkedOpenHashMap<ArrayList<PlayerWatchRecord>>>
        data = new ConcurrentHashMap<>();
    
    private static final List<WeakReference<ChunkLoader>>
        additionalChunkLoaders = new CopyOnWriteArrayList<>();
    
    private static volatile boolean isUpdateRequested = false;
    
    public static class PlayerInfo {
        public final Set<ResourceKey<Level>> visibleDimensions = new HashSet<>();
        public final ArrayList<ChunkLoader> additionalChunkLoaders
            = new ArrayList<>();
        public final ArrayList<ArrayDeque<PlayerWatchRecord>> distanceToPendingChunks =
            new ArrayList<>();
        
        // normally chunk loading will update following to an interval
        // but if this is true, it will immediately update next tick
        public boolean shouldUpdateImmediately = false;
        
        public int cameraEntityId = -1;
        public long cameraChunk = Long.MIN_VALUE;
        
        public PerformanceLevel performanceLevel = PerformanceLevel.bad;
        
        public PlayerInfo() {
        }
        
        // one chunk may mark pending loading multiple times with different distanceToSource
        public void markPendingLoading(PlayerWatchRecord record) {
            Helper.arrayListComputeIfAbsent(
                distanceToPendingChunks,
                record.distanceToSource,
                ArrayDeque::new
            ).add(record);
        }
    }
    
    private static final WeakHashMap<ServerPlayer, PlayerInfo> playerInfoMap = new WeakHashMap<>();
    
    public static final SignalBiArged<ServerPlayer, DimensionalChunkPos> beginWatchChunkSignal = new SignalBiArged<>();
    public static final SignalBiArged<ServerPlayer, DimensionalChunkPos> endWatchChunkSignal = new SignalBiArged<>();
    public static final SignalBiArged<ResourceKey<Level>, Long> watchStatusChangeSignal = new SignalBiArged<>();
    
    private static Long2ObjectLinkedOpenHashMap<ArrayList<PlayerWatchRecord>> getChunkRecordMap(ResourceKey<Level> dimension) {
        return data.computeIfAbsent(dimension, k -> new Long2ObjectLinkedOpenHashMap<>());
    }
    
    @Nullable
    private static ArrayList<PlayerWatchRecord> getChunkRecords(ResourceKey<Level> dimension, long chunkPos) {
        Long2ObjectLinkedOpenHashMap<ArrayList<PlayerWatchRecord>> chunkRecordMap = data.get(dimension);
        return chunkRecordMap == null ? null : chunkRecordMap.get(chunkPos);
    }
    
    public static PlayerInfo getPlayerInfo(ServerPlayer player) {
        return playerInfoMap.computeIfAbsent(player, k -> new PlayerInfo());
    }
    
    public static void updateForPlayer(ServerPlayer player) {
        PlayerInfo playerInfo = getPlayerInfo(player);
        playerInfo.visibleDimensions.clear();
        
        long gameTime = McHelper.getOverWorldOnServer().getGameTime();
        ChunkVisibility.getBaseChunkLoaders(player)
            .forEach(chunkLoader -> updatePlayerForChunkLoader(player, gameTime, chunkLoader, playerInfo));
        
        playerInfo.additionalChunkLoaders.forEach(l -> {
            ChunkLoader chunkLoader = l;
            Validate.notNull(chunkLoader);
            updatePlayerForChunkLoader(player, gameTime, chunkLoader, playerInfo);
        });
    }
    
    public static void flushPendingLoading(ServerPlayer player) {
        PlayerInfo playerInfo = getPlayerInfo(player);
        
        final int limit = getChunkDeliveringLimitPerTick(player);
        int loaded = 0;
        int directLoaded = 0;
        
        for (int distance = 0; distance < playerInfo.distanceToPendingChunks.size(); distance++) {
            ArrayDeque<PlayerWatchRecord> records = playerInfo.distanceToPendingChunks.get(distance);
            if (records != null) {
                while (!records.isEmpty() && loaded < limit && directLoaded < 5) {
                    PlayerWatchRecord record = records.pollFirst();
                    if (record.isValid && !record.isLoadedToPlayer) {
                        record.isLoadedToPlayer = true;
                        
                        if (MiscHelper.getServer().getLevel(record.dimension) != null) {
                            beginWatchChunkSignal.emit(player, new DimensionalChunkPos(
                                record.dimension, new ChunkPos(record.chunkPos)
                            ));
                            if (!record.isDirectLoading) {
                                MyLoadingTicket.addTicketIfNotLoaded(
                                    McHelper.getServerWorld(record.dimension),
                                    new ChunkPos(record.chunkPos)
                                );
                            }
                            watchStatusChangeSignal.emit(
                                record.dimension, record.chunkPos
                            );
                            
                            if (!record.isDirectLoading) {
                                loaded++;
                            }
                            else {
                                directLoaded++;
                            }
                        }
                        else {
                            Helper.err(
                                "Missing dimension when flushing pending loading " + record.dimension.location()
                            );
                        }
                    }
                }
            }
        }
    }
    
    private static final Random random = new Random();
    
    private static int getChunkDeliveringLimitPerTick(ServerPlayer player) {
        if (player.tickCount < 100) {
            return 200;
        }
        
        PlayerInfo playerInfo = getPlayerInfo(player);
        
        if (playerInfo.performanceLevel == PerformanceLevel.good) {
            return 5;
        }
        else if (playerInfo.performanceLevel == PerformanceLevel.medium) {
            return 1;
        }
        else {
            return player.tickCount % 4 == 0 ? 1 : 0;
        }
    }
    
    private static void updatePlayerForChunkLoader(
        ServerPlayer player, long gameTime, ChunkLoader chunkLoader,
        PlayerInfo playerInfo
    ) {
        ResourceKey<Level> chunkLoaderDim = chunkLoader.center.dimension;
        // a player who died in a level that another mod has already removed still stands in it until respawn
        if (MiscHelper.getServer().getLevel(chunkLoaderDim) == null) {
            return;
        }
        playerInfo.visibleDimensions.add(chunkLoaderDim);
        
        Long2ObjectLinkedOpenHashMap<ArrayList<PlayerWatchRecord>> chunkRecordMap =
            getChunkRecordMap(chunkLoaderDim);
        
        chunkLoader.foreachChunkPos(
            (dimension, x, z, distanceToSource) -> {
                long chunkPos = ChunkPos.asLong(x, z);
                ArrayList<PlayerWatchRecord> records = chunkRecordMap.computeIfAbsent(
                    chunkPos,
                    k -> new ArrayList<>()
                );
                
                int index = Helper.indexOf(records, r -> r.player == player);
                if (index == -1) {
                    PlayerWatchRecord newRecord = new PlayerWatchRecord(
                        player, dimension, chunkPos, gameTime, distanceToSource, chunkLoader.isDirectLoader,
                        false
                    );
                    records.add(newRecord);
                    playerInfo.markPendingLoading(newRecord);
                }
                else {
                    PlayerWatchRecord record = records.get(index);
                    
                    if (record.lastWatchTime == gameTime) {
                        //being updated again in the same turn
                        int oldDistance = record.distanceToSource;
                        if (distanceToSource < oldDistance) {
                            record.distanceToSource = distanceToSource;
                            playerInfo.markPendingLoading(record);
                        }
                        
                        record.isDirectLoading = (record.isDirectLoading | chunkLoader.isDirectLoader);
                    }
                    else {
                        //being updated at the first time in this turn
                        int oldDistance = record.distanceToSource;
                        if (distanceToSource < oldDistance) {
                            playerInfo.markPendingLoading(record);
                        }
                        
                        record.distanceToSource = distanceToSource;
                        record.lastWatchTime = gameTime;
                        record.isDirectLoading = chunkLoader.isDirectLoader;
                    }
                }
            }
        );
    }
    
    private static void updateAndPurge() {
        long currTime = McHelper.getOverWorldOnServer().getGameTime();
        data.forEach((dimension, chunkRecords) -> {
            chunkRecords.long2ObjectEntrySet().removeIf(entry -> {
                long chunkPosLong = entry.getLongKey();
                
                ArrayList<PlayerWatchRecord> records = entry.getValue();
                
                removeInactiveWatchers(
                    records,
                    (record) -> {
                        return shouldUnload(currTime, record);
                    },
                    (record) -> {
                        if (record.player.isRemoved()) return;
                        
                        if (record.isLoadedToPlayer) {
                            endWatchChunkSignal.emit(
                                record.player,
                                new DimensionalChunkPos(
                                    dimension,
                                    ChunkPos.getX(chunkPosLong),
                                    ChunkPos.getZ(chunkPosLong)
                                )
                            );
                        }
                        
                        watchStatusChangeSignal.emit(
                            record.dimension, record.chunkPos
                        );
                    }
                );
                
                return records.isEmpty();
            });
        });
        
        MiscHelper.getServer().getAllLevels().forEach(world -> {
            
            Long2ObjectLinkedOpenHashMap<ArrayList<PlayerWatchRecord>> chunkRecordMap = getChunkRecordMap(world.dimension());
            
            LongSortedSet additionalLoadedChunks = new LongLinkedOpenHashSet();
            additionalChunkLoaders.forEach(weakRef -> {
                ChunkLoader loader = weakRef.get();
                if (loader == null) return;
                loader.foreachChunkPos(
                    (dim, x, z, dis) -> {
                        if (world.dimension() == dim) {
                            additionalLoadedChunks.add(ChunkPos.asLong(x, z));
                            MyLoadingTicket.addTicketIfNotLoaded(world, new ChunkPos(x, z));
                            watchStatusChangeSignal.emit(
                                dim, ChunkPos.asLong(x, z)
                            );
                        }
                    }
                );
            });
            additionalChunkLoaders.removeIf(ref -> ref.get() == null);
            
            LongList chunksToUnload = new LongArrayList();
            MyLoadingTicket.getRecord(world).forEach((long longChunkPos) -> {
                if (!chunkRecordMap.containsKey(longChunkPos) &&
                    !additionalLoadedChunks.contains(longChunkPos)
                ) {
                    chunksToUnload.add(longChunkPos);
                }
            });
            
            chunksToUnload.forEach((long longChunkPos) -> {
                MyLoadingTicket.removeTicketIfPresent(world, new ChunkPos(longChunkPos));
            });
        });
        
        playerInfoMap.entrySet().removeIf(e -> e.getKey().isRemoved());
    }
    
    private static boolean shouldUnload(long currTime, PlayerWatchRecord record) {
        if (record.player.isRemoved()) {
            return true;
        }
        long unloadDelay = IPGlobal.chunkUnloadDelayTicks;
        
        if (unloadDelay < updateInterval + 1) {
            unloadDelay = updateInterval + 1;
        }
        
        if (GcMonitor.isMemoryNotEnough()) {
            // does not delay unloading
            unloadDelay = updateInterval + 1;
        }
        
        return currTime - record.lastWatchTime > unloadDelay;
    }
    
    private static void tick() {
        MiscHelper.getServer().getProfiler().push("portal_chunk_tracking");
        
        long gameTime = McHelper.getOverWorldOnServer().getGameTime();
        McHelper.getCopiedPlayerList().forEach(player -> {
            PlayerInfo playerInfo = getPlayerInfo(player);
            
            // spread the player updates to different ticks
            if (hasCameraMoved(player, playerInfo) || playerInfo.shouldUpdateImmediately ||
                (player.getId() % updateInterval == gameTime % updateInterval)
            ) {
                playerInfo.shouldUpdateImmediately = false;
                updateForPlayer(player);
            }
            flushPendingLoading(player);
        });
        if (isUpdateRequested || gameTime % updateInterval == 0) {
            isUpdateRequested = false;
            updateAndPurge();
        }
        
        MiscHelper.getServer().getProfiler().pop();
    }
    
    // a camera entity that changed or entered another chunk is followed at once, not at the next periodic update
    private static boolean hasCameraMoved(ServerPlayer player, PlayerInfo playerInfo) {
        Entity camera = player.getCamera();
        long cameraChunk = camera.chunkPosition().toLong();
        if (camera.getId() == playerInfo.cameraEntityId && (camera == player || cameraChunk == playerInfo.cameraChunk)) {
            return false;
        }
        playerInfo.cameraEntityId = camera.getId();
        playerInfo.cameraChunk = cameraChunk;
        return true;
    }
    
    public static void init() {
        IPGlobal.postServerTickSignal.connect(NewChunkTrackingGraph::tick);
        IPGlobal.serverCleanupSignal.connect(NewChunkTrackingGraph::cleanup);
    }
    
    public static boolean isPlayerWatchingChunk(
        ServerPlayer player,
        ResourceKey<Level> dimension,
        int x, int z,
        Predicate<PlayerWatchRecord> predicate
    ) {
        long chunkPos = ChunkPos.asLong(x, z);
        
        ArrayList<PlayerWatchRecord> recordMap = getChunkRecords(dimension, chunkPos);
        if (recordMap == null) {
            return false;
        }
        int i = Helper.indexOf(recordMap, r -> r.player == player);
        if (i == -1) {
            return false;
        }
        
        PlayerWatchRecord record = recordMap.get(i);
        
        if (!record.isLoadedToPlayer) {
            return false;
        }
        
        return predicate.test(record);
    }
    
    public static boolean isPlayerWatchingChunk(
        ServerPlayer player,
        ResourceKey<Level> dimension,
        int x, int z
    ) {
        return isPlayerWatchingChunk(player, dimension, x, z, r -> true);
    }
    
    public static boolean isPlayerWatchingChunkWithinRaidus(
        ServerPlayer player,
        ResourceKey<Level> dimension,
        int x, int z,
        int radiusBlocks
    ) {
        boolean result = isPlayerWatchingChunk(
            player, dimension, x, z,
            r -> r.distanceToSource * 16 <= radiusBlocks
        );
        return result;
    }
    
    private static void cleanup() {
        data.clear();
        additionalChunkLoaders.clear();
    }
    
    public static Stream<ServerPlayer> getPlayersViewingChunk(
        ResourceKey<Level> dimension,
        int x, int z
    ) {
        ArrayList<PlayerWatchRecord> records = getChunkRecords(dimension, ChunkPos.asLong(x, z));
        if (records == null) {
            return Stream.empty();
        }
        return records.stream().filter(r -> r.isLoadedToPlayer).map(r -> r.player);
    }
    
    // return -1 for none
    public static int getMinimumWatchingDistance(
        ResourceKey<Level> dimension,
        long chunkPos
    ) {
        ArrayList<PlayerWatchRecord> records = getChunkRecords(dimension, chunkPos);
        if (records == null) {
            return -1;
        }
        
        return records.stream().filter(r -> r.isLoadedToPlayer)
            .mapToInt(r -> r.distanceToSource).min().orElse(-1);
    }
    
    /**
     * {@link net.minecraft.server.world.ThreadedAnvilChunkStorage#getPlayersWatchingChunk(ChunkPos, boolean)}
     * The "onlyOnWatchDistanceEdge" is so weird!!!!!!
     * If it does not send only to edge players, placing a block will
     * send light updates and cause client to rebuild the chunk multiple times
     */
    public static Stream<ServerPlayer> getFarWatchers(
        ResourceKey<Level> dimension,
        int x, int z
    ) {
        return getPlayersViewingChunk(dimension, x, z)
            .filter(player -> {
                ChunkPos chunkPos = player.chunkPosition();
                return player.level.dimension() != dimension ||
                    Helper.getChebyshevDistance(x, z, chunkPos.x, chunkPos.z) > 4;
            });
    }
    
    public static void forceRemovePlayer(ServerPlayer player) {
        Helper.log("Chunk Tracking Graph Force Remove " + player.getName().getContents());
        data.forEach((dim, map) -> map.forEach(
            (chunkPos, records) -> removeInactiveWatchers(
                records,
                (r) -> r.player == player,
                record -> {
                    record.isValid = false;
                    PacketRedirection.sendRedirectedMessage(
                        record.player, dim, new ClientboundForgetLevelChunkPacket(
                            ChunkPos.getX(chunkPos),
                            ChunkPos.getZ(chunkPos)
                        )
                    );
                }
            )
        ));
    }
    
    public static void forceRemoveDimension(ResourceKey<Level> dim) {
        Long2ObjectLinkedOpenHashMap<ArrayList<PlayerWatchRecord>> map = data.get(dim);
        
        if (map == null) {
            return;
        }
        
        map.forEach((chunkPos, records) -> {
            Packet unloadPacket = PacketRedirection.createRedirectedMessage(
                dim, new ClientboundForgetLevelChunkPacket(
                    ChunkPos.getX(chunkPos),
                    ChunkPos.getZ(chunkPos)
                )
            );
            for (PlayerWatchRecord record : records) {
                if (record.isValid && record.isLoadedToPlayer) {
                    record.player.connection.send(unloadPacket);
                }
                record.isValid = false;
            }
        });
        
        data.remove(dim);
        
        additionalChunkLoaders.removeIf(l -> {
            ChunkLoader chunkLoader = l.get();
            return chunkLoader != null && chunkLoader.center.dimension == dim;
        });
        
        for (PlayerInfo playerInfo : playerInfoMap.values()) {
            playerInfo.additionalChunkLoaders.removeIf(l -> l.center.dimension == dim);
        }
    }
    
    public static boolean shouldLoadDimension(ResourceKey<Level> dimension) {
        if (!data.containsKey(dimension)) {
            return false;
        }
        Long2ObjectLinkedOpenHashMap<ArrayList<PlayerWatchRecord>> map =
            data.get(dimension);
        return !map.isEmpty();
    }
    
    // applied at the end of this tick: it can be called while levels tick on other threads
    public static void addGlobalAdditionalChunkLoader(ChunkLoader chunkLoader) {
        additionalChunkLoaders.add(new WeakReference<>(chunkLoader));
        isUpdateRequested = true;
    }
    
    // if this method is accidentally not called
    // the chunk loader will still be removed if it's not GCed (maybe after a long time)
    public static void removeGlobalAdditionalChunkLoader(ChunkLoader chunkLoader) {
        // WeakReference does not have equals()
        additionalChunkLoaders.removeIf(weakRef -> weakRef.get() == chunkLoader);
    }
    
    // When changing a player's dimension on server, it will remove all
    // loading tickets of this player. Without this, the chunks nearby player
    // may have no ticket for a short period of time (because the chunk tracking refreshes
    // every 2 seconds) and the chunk may be unloaded and reloaded.
    public static void addAdditionalDirectLoadingTickets(ServerPlayer player) {
        ChunkVisibility.playerDirectLoader(player).foreachChunkPos((dim, x, z, dis) -> {
            if (isPlayerWatchingChunk(player, dim, x, z)) {
                
                MyLoadingTicket.addTicketIfNotLoaded(((ServerLevel) player.level), new ChunkPos(x, z));
            }
        });
    }
    
    public static int getLoadedChunkNum(ResourceKey<Level> dimension) {
        return getChunkRecordMap(dimension).size();
    }
    
    public static void addPerPlayerAdditionalChunkLoader(
        ServerPlayer player,
        ChunkLoader chunkLoader
    ) {
        PlayerInfo playerInfo = getPlayerInfo(player);
        playerInfo.additionalChunkLoaders.add(chunkLoader);
        playerInfo.shouldUpdateImmediately = true;
    }
    
    /**
     * NOTE it removes chunk loader by object reference, not by value equality
     */
    public static void removePerPlayerAdditionalChunkLoader(
        ServerPlayer player,
        ChunkLoader chunkLoader
    ) {
        getPlayerInfo(player).additionalChunkLoaders.removeIf(c -> c == chunkLoader);
    }
    
    public static Set<ResourceKey<Level>> getVisibleDimensions(ServerPlayer player) {
        return getPlayerInfo(player).visibleDimensions;
    }
    
    public static class RemoteCallables {
        public static void acceptClientPerformanceInfo(
            ServerPlayer player,
            PerformanceLevel performanceLevel
        ) {
            PlayerInfo playerInfo = getPlayerInfo(player);
            playerInfo.performanceLevel = performanceLevel;
        }
    }
}
