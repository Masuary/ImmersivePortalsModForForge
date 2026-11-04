package qouteall.imm_ptl.core.block_manipulation;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.game.ClientboundBlockBreakAckPacket;
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemOnPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Tuple;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import qouteall.imm_ptl.core.IPMcHelper;
import qouteall.imm_ptl.core.compat.PehkuiInterface;
import qouteall.imm_ptl.core.compat.vault_hunters.VaultRules;
import qouteall.imm_ptl.core.network.PacketRedirection;
import qouteall.imm_ptl.core.portal.Portal;
import qouteall.imm_ptl.core.portal.global_portals.GlobalPortalStorage;
import qouteall.q_misc_util.MiscHelper;
import qouteall.q_misc_util.my_util.LimitedLogger;

import java.util.List;

public class BlockManipulationServer {
    private static final LimitedLogger rejectionLogger = new LimitedLogger(100);
    
    public static void processBreakBlock(
        ResourceKey<Level> dimension,
        ServerboundPlayerActionPacket packet,
        ServerPlayer player
    ) {
        // checked before shouldFinishMining, which already attacks the block
        if (!canPlayerReach(dimension, player, packet.getPos(), VaultRules.BREAK_REACH_PADDING)) {
            rejectionLogger.log("Rejected cross portal block breaking packet " + player);
            sendBreakAck(dimension, packet, player, "out of reach");
            return;
        }
        if (shouldFinishMining(dimension, packet, player)) {
            doDestroyBlock(dimension, packet, player);
        }
    }
    
    private static boolean shouldFinishMining(
        ResourceKey<Level> dimension,
        ServerboundPlayerActionPacket packet,
        ServerPlayer player
    
    ) {
        if (packet.getAction() == ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK) {
            return canInstantMine(
                MiscHelper.getServer().getLevel(dimension),
                player,
                packet.getPos()
            );
        }
        else {
            return packet.getAction() == ServerboundPlayerActionPacket.Action.STOP_DESTROY_BLOCK;
        }
    }
    
    private static void sendBreakAck(
        ResourceKey<Level> dimension,
        ServerboundPlayerActionPacket packet,
        ServerPlayer player,
        String reason
    ) {
        ServerLevel destWorld = MiscHelper.getServer().getLevel(dimension);
        ClientboundBlockBreakAckPacket ackPacket = new ClientboundBlockBreakAckPacket(
            packet.getPos(), destWorld.getBlockState(packet.getPos()), packet.getAction(), false, reason);
        player.connection.send(PacketRedirection.createRedirectedMessage(dimension, ackPacket));
    }
    
    private static boolean canPlayerReach(
        ResourceKey<Level> dimension,
        ServerPlayer player,
        BlockPos requestPos,
        double vaultReachPadding
    ) {
        if (VaultRules.involvesVaultHuntersDimension(player.level.dimension().location(), dimension.location())) {
            return false;
        }
        
        Float playerScale = PehkuiInterface.invoker.computeBlockReachScale(player);
        
        Vec3 pos = Vec3.atCenterOf(requestPos);
        boolean isReachCapped = VaultRules.isVaultHuntersPresent();
        Vec3 playerPos = isReachCapped ? player.getEyePosition() : player.position();
        double reach = isReachCapped ? VaultRules.capRemoteReach(player.getReachDistance()) + vaultReachPadding : 6 * 4;
        double distanceSquare = reach * reach * playerScale * playerScale;
        if (player.level.dimension() == dimension) {
            if (playerPos.distanceToSqr(pos) < distanceSquare) {
                return true;
            }
        }
        return IPMcHelper.getNearbyPortals(
            player,
            20
        ).anyMatch(portal ->
            portal.dimensionTo == dimension &&
                portal.transformPoint(playerPos).distanceToSqr(pos) <
                    distanceSquare * portal.getScale() * portal.getScale()
        );
    }
    
    // vanilla copy
    private static void doDestroyBlock(
        ResourceKey<Level> dimension,
        ServerboundPlayerActionPacket packet,
        ServerPlayer player
    ) {
        ServerLevel destWorld = MiscHelper.getServer().getLevel(dimension);
        ServerLevel oldWorld = player.getLevel();
        
        BlockPos blockPos = packet.getPos();
        
        if (destWorld.mayInteract(player, blockPos)) {
            player.gameMode.setLevel(destWorld);
            player.gameMode.destroyBlock(
                blockPos
            );
            player.gameMode.setLevel(oldWorld);
        }
        else {
            sendBreakAck(dimension, packet, player, "may not interact");
        }
    }
    
    private static boolean canInstantMine(
        ServerLevel world,
        ServerPlayer player,
        BlockPos pos
    ) {
        if (player.isCreative()) {
            return true;
        }
        
        float progress = 1.0F;
        BlockState blockState = world.getBlockState(pos);
        if (!blockState.isAir()) {
            blockState.attack(world, pos, player);
            progress = blockState.getDestroyProgress(player, world, pos);
        }
        return !blockState.isAir() && progress >= 1.0F;
    }
    
    public static Tuple<BlockHitResult, ResourceKey<Level>> getHitResultForPlacing(
        Level world,
        BlockHitResult blockHitResult
    ) {
        Direction side = blockHitResult.getDirection();
        Vec3 sideVec = Vec3.atLowerCornerOf(side.getNormal());
        Vec3 hitCenter = Vec3.atCenterOf(blockHitResult.getBlockPos());
        
        List<Portal> globalPortals = GlobalPortalStorage.getGlobalPortals(world);
        
        Portal portal = globalPortals.stream().filter(p ->
            p.getNormal().dot(sideVec) < -0.9
                && p.isPointInPortalProjection(hitCenter)
                && p.getDistanceToPlane(hitCenter) < 0.6
        ).findFirst().orElse(null);
        
        if (portal == null) {
            return new Tuple<>(blockHitResult, world.dimension());
        }
        
        Vec3 newCenter = portal.transformPoint(hitCenter.add(sideVec.scale(0.501)));
        BlockPos placingBlockPos = new BlockPos(newCenter);
        
        BlockHitResult newHitResult = new BlockHitResult(
            Vec3.ZERO,
            side.getOpposite(),
            placingBlockPos,
            blockHitResult.isInside()
        );
        
        return new Tuple<>(newHitResult, portal.dimensionTo);
    }
    
    public static void processRightClickBlock(
        ResourceKey<Level> dimension,
        ServerboundUseItemOnPacket packet,
        ServerPlayer player
    ) {
        InteractionHand hand = packet.getHand();
        BlockHitResult blockHitResult = packet.getHitResult();
        
        ServerLevel world = MiscHelper.getServer().getLevel(dimension);
        
        doProcessRightClick(dimension, player, hand, blockHitResult);
    }
    
    // vanilla copy
    
    /**
     * {@link net.minecraft.server.network.ServerGamePacketListenerImpl#handleUseItemOn(ServerboundUseItemOnPacket)}
     */
    public static void doProcessRightClick(
        ResourceKey<Level> dimension,
        ServerPlayer player,
        InteractionHand hand,
        BlockHitResult blockHitResult
    ) {
        ItemStack itemStack = player.getItemInHand(hand);
        
        MinecraftServer server = MiscHelper.getServer();
        ServerLevel targetWorld = server.getLevel(dimension);
        
        BlockPos blockPos = blockHitResult.getBlockPos();
        Direction direction = blockHitResult.getDirection();
        player.resetLastActionTime();
        if (!canPlayerReach(dimension, player, blockPos, VaultRules.USE_REACH_PADDING)) {
            rejectionLogger.log("Reject cross portal block placing packet " + player);
            // the client already predicted the use, e.g. took the placed item out of the stack
            player.containerMenu.sendAllDataToRemote();
        }
        else if (targetWorld.mayInteract(player, blockPos)) {
            Level oldWorld = player.level;
            
            player.level = targetWorld;
            try {
                InteractionResult actionResult = player.gameMode.useItemOn(
                    player,
                    targetWorld,
                    itemStack,
                    hand,
                    blockHitResult
                );
                if (actionResult.shouldSwing()) {
                    player.swing(hand, true);
                }
            }
            finally {
                player.level = oldWorld;
            }
        }
        
        PacketRedirection.sendRedirectedMessage(
            player,
            dimension,
            new ClientboundBlockUpdatePacket(targetWorld, blockPos)
        );
        
        BlockPos offseted = blockPos.relative(direction);
        if (offseted.getY() >= targetWorld.getMinBuildHeight() && offseted.getY() < targetWorld.getMaxBuildHeight()) {
            PacketRedirection.sendRedirectedMessage(
                player,
                dimension,
                new ClientboundBlockUpdatePacket(targetWorld, offseted)
            );
        }
    }
}
