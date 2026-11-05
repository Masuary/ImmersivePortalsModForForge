package qouteall.imm_ptl.core.compat.mixin;

import iskallia.vault.block.VaultPortalBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import qouteall.imm_ptl.core.compat.vault_hunters.VaultPortalLinks;

@Mixin(VaultPortalBlock.class)
public class MixinVaultPortalBlock {

    @Inject(method = "entityInside", at = @At("HEAD"), cancellable = true)
    private void ip_leaveCoveredBlocksToWindow(BlockState state, Level level, BlockPos pos, Entity entity, CallbackInfo ci) {
        if (!level.isClientSide && VaultPortalLinks.isTouchLeftToWindow(entity, level, pos)) {
            ci.cancel();
        }
    }

    // the crossing player's server position is still a little short of the portal slab when the window is crossed
    @Redirect(
        method = "entityInside",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/phys/shapes/Shapes;joinIsNotEmpty(Lnet/minecraft/world/phys/shapes/VoxelShape;Lnet/minecraft/world/phys/shapes/VoxelShape;Lnet/minecraft/world/phys/shapes/BooleanOp;)Z"
        )
    )
    private boolean ip_crossingTouchesPortal(
        VoxelShape playerShape, VoxelShape portalShape, BooleanOp operation,
        BlockState state, Level level, BlockPos pos, Entity entity
    ) {
        return VaultPortalLinks.isCrossing(entity) || Shapes.joinIsNotEmpty(playerShape, portalShape, operation);
    }

    @Inject(method = "entityInside", at = @At("RETURN"))
    private void ip_linkTouchedFrame(BlockState state, Level level, BlockPos pos, Entity entity, CallbackInfo ci) {
        if (level instanceof ServerLevel serverLevel && entity instanceof ServerPlayer player) {
            VaultPortalLinks.onPortalBlockTouched(serverLevel, pos, player);
        }
    }
}
