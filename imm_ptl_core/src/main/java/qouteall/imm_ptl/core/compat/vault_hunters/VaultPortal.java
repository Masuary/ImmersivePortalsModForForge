package qouteall.imm_ptl.core.compat.vault_hunters;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import qouteall.imm_ptl.core.compat.vault_hunters.VaultRules.VaultPortalRole;
import qouteall.imm_ptl.core.platform_specific.IPRegistry;
import qouteall.imm_ptl.core.portal.Portal;
import qouteall.imm_ptl.core.portal.PortalExtension;
import qouteall.q_misc_util.Helper;

import java.util.UUID;

/**
 * A see-through portal laid over a Vault Hunters portal: a hub frame (ENTRY, into the vault's start room) or the
 * start-room portal (EXIT, back to the frame). Every crossing is decided by Vault Hunters' own logic for that portal
 * block (join checks, bail and exit objectives); this class holds no Vault Hunters types, so the entity type can be
 * registered without Vault Hunters, and all decisions are made in {@link VaultPortalLinks}.
 */
public class VaultPortal extends Portal {
    private static final int VALIDATION_INTERVAL_TICKS = 20;

    private VaultPortalRole role = VaultPortalRole.ENTRY;
    private UUID vaultId;
    // the Vault Hunters portal block whose own logic runs for each crossing
    private BlockPos vaultHuntersBlock;

    public VaultPortal(EntityType<?> entityType, Level level) {
        super(entityType, level);
    }

    public static VaultPortal create(
        ServerLevel level, VaultPortalRole role, UUID vaultId, BlockPos vaultHuntersBlock,
        VaultPortalGeometry.Placement placement, ResourceKey<Level> destinationDimension
    ) {
        VaultPortal portal = IPRegistry.VAULT_PORTAL.get().create(level);
        if (portal == null) {
            throw new IllegalStateException("Could not create a vault portal entity in " + level.dimension().location());
        }
        portal.role = role;
        portal.vaultId = vaultId;
        portal.vaultHuntersBlock = vaultHuntersBlock;
        portal.setOriginPos(placement.origin());
        portal.axisW = placement.axisW();
        portal.axisH = placement.axisH();
        portal.width = placement.width();
        portal.height = placement.height();
        portal.dimensionTo = destinationDimension;
        portal.setDestination(placement.destination());
        portal.setRotationTransformationD(placement.rotation());
        portal.setInteractable(false);
        PortalExtension.get(portal).adjustPositionAfterTeleport = false;
        return portal;
    }

    public VaultPortalRole getRole() {
        return role;
    }

    public UUID getVaultId() {
        return vaultId;
    }

    public BlockPos getVaultHuntersBlock() {
        return vaultHuntersBlock;
    }

    @Override
    public boolean canTeleportEntity(Entity entity) {
        return super.canTeleportEntity(entity) && VaultRules.mayUseVaultPortal(entity instanceof Player, entity.isSpectator());
    }

    // whether the box is at this window rather than beside it (see VaultPortalGeometry.isBoxAtWindow)
    public boolean isBoxAtWindow(AABB box, double maxPlaneDistance) {
        return VaultPortalGeometry.isBoxAtWindow(getOriginPos(), axisW, axisH, width, height, box, maxPlaneDistance);
    }

    /**
     * Runs Vault Hunters' own logic for this portal for a player whose client has just crossed it at
     * {@code crossingEyePos}.
     *
     * @return whether the crossing may complete
     */
    public boolean admitCrossingOnServer(ServerPlayer player, Vec3 crossingEyePos) {
        return VaultPortalLinks.admitCrossing(player, this, crossingEyePos);
    }

    @Override
    public void onEntityTeleportedOnServer(Entity entity) {
        super.onEntityTeleportedOnServer(entity);
        if (entity instanceof ServerPlayer player) {
            VaultPortalLinks.onCrossed(player, this);
        }
    }

    @Override
    public void tick() {
        super.tick();
        if (level.isClientSide || isRemoved()) {
            return;
        }
        if (vaultId == null || vaultHuntersBlock == null) {
            Helper.err("Removed a vault portal without its vault data " + this);
            remove(RemovalReason.DISCARDED);
            return;
        }
        if (tickCount % VALIDATION_INTERVAL_TICKS == 0) {
            VaultPortalLinks.removeIfStale(this);
        }
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        // a portal missing these keys was not made by VaultPortalLinks; it removes itself on its next server tick
        if (tag.contains("vaultPortalRole") && tag.hasUUID("vaultId") && tag.contains("vaultHuntersBlock")) {
            role = VaultPortalRole.valueOf(tag.getString("vaultPortalRole"));
            vaultId = tag.getUUID("vaultId");
            vaultHuntersBlock = NbtUtils.readBlockPos(tag.getCompound("vaultHuntersBlock"));
        }
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        if (vaultId != null && vaultHuntersBlock != null) {
            tag.putString("vaultPortalRole", role.name());
            tag.putUUID("vaultId", vaultId);
            tag.put("vaultHuntersBlock", NbtUtils.writeBlockPos(vaultHuntersBlock));
        }
    }
}
