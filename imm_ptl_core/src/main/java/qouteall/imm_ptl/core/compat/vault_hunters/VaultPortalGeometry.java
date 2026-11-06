package qouteall.imm_ptl.core.compat.vault_hunters;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import qouteall.q_misc_util.my_util.DQuaternion;

import javax.annotation.Nullable;

/**
 * Places a see-through portal over a Vault Hunters portal opening (a hub frame or a vault's start-room portal) that
 * leads to another opening. Both sides use the same size, centred and bottom-aligned so the floors line up: the smaller
 * of the two openings, or for a hub frame's entry window the whole frame where the start room has room for it. Pure
 * geometry without Vault Hunters classes, so it is unit tested.
 */
public final class VaultPortalGeometry {
    // Vault Hunters' portal block is a nether-portal slab (6/16 to 10/16 across); the portal plane sits just in front
    // of it on the viewer's side, so the swirl texture stays behind the portal surface
    public static final double SURFACE_OFFSET = 0.13;
    // a fitted entry window ends in the start room past the wall the start-room portal sits in, far enough that a
    // player (0.3 blocks from the centre to the side) arriving there is clear of the wall, the portal and its window
    public static final double ROOM_ARRIVAL_OFFSET = 0.5 + 0.3 + 0.01;
    private static final Vec3 UP = new Vec3(0, 1, 0);

    /**
     * A vertical portal opening made of whole blocks (corners inclusive) whose plane is perpendicular to
     * {@code normalAxis} (a horizontal axis).
     */
    public record Opening(BlockPos min, BlockPos max, Direction.Axis normalAxis) {
        public Opening {
            if (!normalAxis.isHorizontal()) {
                throw new IllegalArgumentException("A vault portal opening must be vertical, its normal axis is " + normalAxis);
            }
        }

        public double width() {
            return normalAxis == Direction.Axis.Z ? max.getX() - min.getX() + 1 : max.getZ() - min.getZ() + 1;
        }

        public double height() {
            return max.getY() - min.getY() + 1;
        }

        // the centre of the opening's bottom edge, in the middle of the blocks' thickness
        public Vec3 bottomCentre() {
            if (normalAxis == Direction.Axis.Z) {
                return new Vec3((min.getX() + max.getX() + 1) / 2.0, min.getY(), min.getZ() + 0.5);
            }
            return new Vec3(min.getX() + 0.5, min.getY(), (min.getZ() + max.getZ() + 1) / 2.0);
        }
    }

    public record Placement(
        Vec3 origin, Vec3 axisW, Vec3 axisH, double width, double height,
        Vec3 destination, @Nullable DQuaternion rotation
    ) {
    }

    public record WindowSize(int width, int height) {
    }

    /**
     * Whether the block a player walks into in front of a start-room portal is free, by column (the block offset from
     * the leftmost block the window can cover, see {@link #fitEntryWindow}) and row (above the portal's bottom).
     */
    @FunctionalInterface
    public interface RoomCellTest {
        boolean isFree(int column, int row);
    }

    private VaultPortalGeometry() {
    }

    /**
     * @param viewerSide       the side of {@code from} the portal is seen and entered from
     * @param arrivalDirection the direction a player moves in when coming out of {@code to}
     */
    public static Placement place(Opening from, Direction viewerSide, Opening to, Direction arrivalDirection) {
        return place(from, viewerSide, to, arrivalDirection,
            Math.min(from.width(), to.width()), Math.min(from.height(), to.height()), SURFACE_OFFSET);
    }

    /**
     * A hub frame's entry window of a size from {@link #fitEntryWindow}, ending in the start room in front of its
     * wall (see {@link #ROOM_ARRIVAL_OFFSET}); the window back stays the size of the smaller opening.
     */
    public static Placement placeFittedEntry(
        Opening frame, Direction viewerSide, Opening startRoom, Direction arrivalDirection, WindowSize size
    ) {
        return place(frame, viewerSide, startRoom, arrivalDirection, size.width(), size.height(), ROOM_ARRIVAL_OFFSET);
    }

    /**
     * The largest entry window, up to the whole hub frame and centred on the start-room portal, for which every block a
     * player can arrive in is free; at least the smaller of the two openings, or null when even that is not free.
     * Column 0 of {@code room} is the leftmost block a window of the frame's width can cover.
     */
    @Nullable
    public static WindowSize fitEntryWindow(Opening frame, Opening startRoom, RoomCellTest room) {
        int frameWidth = (int) frame.width();
        int frameHeight = (int) frame.height();
        int minWidth = (int) Math.min(frame.width(), startRoom.width());
        int minHeight = (int) Math.min(frame.height(), startRoom.height());
        int widestFirstColumn = getFirstColumn(startRoom, frameWidth);
        WindowSize best = null;
        for (int width = frameWidth; width >= minWidth; width--) {
            int firstColumn = getFirstColumn(startRoom, width) - widestFirstColumn;
            int columns = getColumnCount(startRoom, width);
            int height = 0;
            while (height < frameHeight && isRowFree(room, firstColumn, columns, height)) {
                height++;
            }
            if (height >= minHeight && (best == null || width * height > best.width() * best.height())) {
                best = new WindowSize(width, height);
            }
        }
        return best;
    }

    // the first block column (along the start-room portal's width) that a centred window of this width covers
    private static int getFirstColumn(Opening opening, double width) {
        return (int) Math.floor(getLateralCentre(opening) - width / 2 + 1.0E-9);
    }

    private static int getColumnCount(Opening opening, double width) {
        return (int) Math.ceil(getLateralCentre(opening) + width / 2 - 1.0E-9) - getFirstColumn(opening, width);
    }

    private static double getLateralCentre(Opening opening) {
        Vec3 bottomCentre = opening.bottomCentre();
        return opening.normalAxis() == Direction.Axis.Z ? bottomCentre.x : bottomCentre.z;
    }

    private static boolean isRowFree(RoomCellTest room, int firstColumn, int columns, int row) {
        for (int column = firstColumn; column < firstColumn + columns; column++) {
            if (!room.isFree(column, row)) {
                return false;
            }
        }
        return true;
    }

    /**
     * The block position of a start-room cell from {@link #fitEntryWindow}: in the layer the player arrives in, in front
     * of the start-room portal on its arrival side.
     */
    public static BlockPos getRoomCell(Opening frame, Opening startRoom, Direction arrivalDirection, int column, int row) {
        int lateral = getFirstColumn(startRoom, frame.width()) + column;
        BlockPos portalLayer = startRoom.normalAxis() == Direction.Axis.Z
            ? new BlockPos(lateral, startRoom.min().getY() + row, startRoom.min().getZ())
            : new BlockPos(startRoom.min().getX(), startRoom.min().getY() + row, lateral);
        return portalLayer.relative(arrivalDirection);
    }

    private static Placement place(
        Opening from, Direction viewerSide, Opening to, Direction arrivalDirection,
        double width, double height, double destinationOffset
    ) {
        if (viewerSide.getAxis() != from.normalAxis() || arrivalDirection.getAxis() != to.normalAxis()) {
            throw new IllegalArgumentException(
                "Sides %s and %s do not face the openings %s and %s".formatted(viewerSide, arrivalDirection, from, to)
            );
        }
        Vec3 normal = Vec3.atLowerCornerOf(viewerSide.getNormal());
        Vec3 arrival = Vec3.atLowerCornerOf(arrivalDirection.getNormal());
        Vec3 halfHeight = new Vec3(0, height / 2, 0);

        Vec3 origin = from.bottomCentre().add(normal.scale(SURFACE_OFFSET)).add(halfHeight);
        Vec3 destination = to.bottomCentre().add(arrival.scale(destinationOffset)).add(halfHeight);
        return new Placement(
            origin, UP.cross(normal), UP, width, height, destination, getYawRotation(normal.scale(-1), arrival)
        );
    }

    /**
     * The direction a player arriving through a vault's start-room portal moves in: the one side of the portal that is
     * open (the room; start-room templates can be rotated, e.g. in Personal vaults), else the vault's facing when the
     * portal faces that way; null when that cannot be told.
     */
    @Nullable
    public static Direction getArrivalDirection(
        Direction vaultFacing, Direction.Axis entranceNormalAxis, boolean isPositiveSideOpen, boolean isNegativeSideOpen
    ) {
        if (!entranceNormalAxis.isHorizontal()) {
            return null;
        }
        if (isPositiveSideOpen != isNegativeSideOpen) {
            return Direction.fromAxisAndDirection(entranceNormalAxis,
                isPositiveSideOpen ? Direction.AxisDirection.POSITIVE : Direction.AxisDirection.NEGATIVE);
        }
        return vaultFacing.getAxis() == entranceNormalAxis ? vaultFacing : null;
    }

    /**
     * Whether a box (a player's, possibly widened) overlaps the window when projected onto its plane and its centre is
     * within {@code maxPlaneDistance} of that plane, i.e. whether the player is at the window rather than beside it.
     */
    public static boolean isBoxAtWindow(
        Vec3 origin, Vec3 axisW, Vec3 axisH, double width, double height, AABB box, double maxPlaneDistance
    ) {
        Vec3 normal = axisW.cross(axisH);
        if (Math.abs(box.getCenter().subtract(origin).dot(normal)) >= maxPlaneDistance) {
            return false;
        }
        double minW = Double.POSITIVE_INFINITY, maxW = Double.NEGATIVE_INFINITY;
        double minH = Double.POSITIVE_INFINITY, maxH = Double.NEGATIVE_INFINITY;
        for (double x : new double[]{box.minX, box.maxX}) {
            for (double y : new double[]{box.minY, box.maxY}) {
                for (double z : new double[]{box.minZ, box.maxZ}) {
                    Vec3 offset = new Vec3(x, y, z).subtract(origin);
                    double w = offset.dot(axisW);
                    double h = offset.dot(axisH);
                    minW = Math.min(minW, w);
                    maxW = Math.max(maxW, w);
                    minH = Math.min(minH, h);
                    maxH = Math.max(maxH, h);
                }
            }
        }
        return maxW > -width / 2 && minW < width / 2 && maxH > -height / 2 && minH < height / 2;
    }

    // the rotation around the vertical axis that turns the direction a player moves into the portal into the arrival
    // direction; null when they are the same
    @Nullable
    private static DQuaternion getYawRotation(Vec3 contentDirection, Vec3 arrival) {
        double angle = Math.atan2(contentDirection.cross(arrival).dot(UP), contentDirection.dot(arrival));
        if (Math.abs(angle) < 1.0E-9) {
            return null;
        }
        return DQuaternion.rotationByRadians(UP, angle);
    }
}
