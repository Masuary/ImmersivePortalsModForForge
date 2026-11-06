package qouteall.imm_ptl.core.compat.vault_hunters;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import qouteall.imm_ptl.core.compat.vault_hunters.VaultPortalGeometry.Opening;
import qouteall.imm_ptl.core.compat.vault_hunters.VaultPortalGeometry.Placement;
import qouteall.imm_ptl.core.compat.vault_hunters.VaultPortalGeometry.RoomCellTest;
import qouteall.imm_ptl.core.compat.vault_hunters.VaultPortalGeometry.WindowSize;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VaultPortalGeometryTest {
    private static final double EPSILON = 1.0E-9;
    // the start-room portal of a VH3 Remastered vault, measured on the testnet (3 x 4 blocks, vault facing north)
    private static final Opening START_ROOM = new Opening(new BlockPos(22, 30, 19), new BlockPos(24, 33, 19), Direction.Axis.Z);
    // a 2 x 3 hub frame opening whose blocks run along X
    private static final Opening HUB_FRAME = new Opening(new BlockPos(499, 200, 500), new BlockPos(500, 202, 500), Direction.Axis.Z);
    // a 3 x 4 hub frame opening whose blocks run along Z
    private static final Opening HUB_FRAME_ALONG_Z = new Opening(new BlockPos(600, 70, 98), new BlockPos(600, 73, 100), Direction.Axis.X);
    // a 5 x 6 hub frame opening, wider and taller than the start-room portal
    private static final Opening WIDE_HUB_FRAME = new Opening(new BlockPos(497, 200, 500), new BlockPos(501, 205, 500), Direction.Axis.Z);

    @Test
    void bothSidesUseTheSmallerOpeningBottomAligned() {
        Placement entry = VaultPortalGeometry.place(HUB_FRAME, Direction.SOUTH, START_ROOM, Direction.NORTH);

        assertEquals(2, entry.width(), EPSILON);
        assertEquals(3, entry.height(), EPSILON);
        assertVec(new Vec3(500, 201.5, 500.5 + VaultPortalGeometry.SURFACE_OFFSET), entry.origin());
        assertVec(new Vec3(23.5, 31.5, 19.5 - VaultPortalGeometry.SURFACE_OFFSET), entry.destination());
    }

    @Test
    void thePortalFacesItsViewerAndStandsUpright() {
        Placement entry = VaultPortalGeometry.place(HUB_FRAME, Direction.SOUTH, START_ROOM, Direction.NORTH);

        assertVec(new Vec3(0, 1, 0), entry.axisH());
        assertVec(Vec3.atLowerCornerOf(Direction.SOUTH.getNormal()), entry.axisW().cross(entry.axisH()));
    }

    @Test
    void walkingIntoTheFrameComesOutWalkingIntoTheVault() {
        for (Direction viewerSide : new Direction[]{Direction.SOUTH, Direction.NORTH}) {
            Placement entry = VaultPortalGeometry.place(HUB_FRAME, viewerSide, START_ROOM, Direction.NORTH);
            Vec3 walking = Vec3.atLowerCornerOf(viewerSide.getOpposite().getNormal());
            assertVec(new Vec3(0, 0, -1), rotate(entry, walking));
            assertVec(new Vec3(0, 1, 0), rotate(entry, new Vec3(0, 1, 0)));
        }
        Placement sideways = VaultPortalGeometry.place(HUB_FRAME_ALONG_Z, Direction.EAST, START_ROOM, Direction.NORTH);
        assertVec(new Vec3(0, 0, -1), rotate(sideways, new Vec3(-1, 0, 0)));
    }

    @Test
    void aFrameFacingTheVaultDirectionNeedsNoRotation() {
        assertNull(VaultPortalGeometry.place(HUB_FRAME, Direction.SOUTH, START_ROOM, Direction.NORTH).rotation());
    }

    @Test
    void theExitWindowIsTheEntryWindowReversed() {
        Placement entry = VaultPortalGeometry.place(HUB_FRAME_ALONG_Z, Direction.WEST, START_ROOM, Direction.NORTH);
        Placement exit = VaultPortalGeometry.place(START_ROOM, Direction.NORTH, HUB_FRAME_ALONG_Z, Direction.WEST);

        assertVec(entry.destination(), exit.origin());
        assertVec(entry.origin(), exit.destination());
        Vec3 hubPoint = new Vec3(600.2, 71.3, 99.1);
        assertVec(hubPoint, transform(exit, transform(entry, hubPoint)));
    }

    @Test
    void theCrossingKeepsTheHeightAboveTheFloor() {
        Placement entry = VaultPortalGeometry.place(HUB_FRAME, Direction.SOUTH, START_ROOM, Direction.NORTH);
        Vec3 eyeAtFrame = new Vec3(500.0, 200 + 1.62, 500.6);

        assertEquals(30 + 1.62, transform(entry, eyeAtFrame).y, EPSILON);
    }

    @Test
    void floorOpeningsAndSidesOffThePlaneAreRefused() {
        assertThrows(IllegalArgumentException.class,
            () -> new Opening(new BlockPos(0, 64, 0), new BlockPos(2, 64, 2), Direction.Axis.Y));
        assertThrows(IllegalArgumentException.class,
            () -> VaultPortalGeometry.place(HUB_FRAME, Direction.EAST, START_ROOM, Direction.NORTH));
    }

    // Vault Hunters' touch is left to the window only for a player at it, not for one beside a wider frame's window
    @Test
    void aPlayerIsAtTheWindowOnlyInFrontOfItsArea() {
        Placement entry = VaultPortalGeometry.place(HUB_FRAME, Direction.SOUTH, START_ROOM, Direction.NORTH);
        AABB walkingUp = new AABB(499.7, 200, 500.9, 500.3, 201.8, 501.5);
        AABB besideTheWindow = walkingUp.move(2.0, 0, 0);
        AABB farInFront = walkingUp.move(0, 0, 3.0);

        assertTrue(isAtWindow(entry, walkingUp.inflate(0.3)));
        assertFalse(isAtWindow(entry, besideTheWindow.inflate(0.3)));
        assertFalse(isAtWindow(entry, farInFront.inflate(0.3)));
    }

    // the room is the one open side of the start-room portal (rotated templates in Personal vaults); the vault facing
    // only decides when both or neither side is open
    @Test
    void theArrivalSideIsTheOneOpenSideOrTheVaultFacing() {
        assertEquals(Direction.NORTH, VaultPortalGeometry.getArrivalDirection(Direction.NORTH, Direction.Axis.Z, false, true));
        assertEquals(Direction.NORTH, VaultPortalGeometry.getArrivalDirection(Direction.NORTH, Direction.Axis.Z, false, false));
        assertEquals(Direction.SOUTH, VaultPortalGeometry.getArrivalDirection(Direction.NORTH, Direction.Axis.Z, true, false));
        assertEquals(Direction.WEST, VaultPortalGeometry.getArrivalDirection(Direction.NORTH, Direction.Axis.X, false, true));
        assertEquals(Direction.EAST, VaultPortalGeometry.getArrivalDirection(Direction.NORTH, Direction.Axis.X, true, false));
        assertNull(VaultPortalGeometry.getArrivalDirection(Direction.NORTH, Direction.Axis.X, true, true));
        assertNull(VaultPortalGeometry.getArrivalDirection(Direction.NORTH, Direction.Axis.X, false, false));
    }

    @Test
    void aRotatedStartRoomGetsAWindowTurnedTowardsTheRoom() {
        Opening rotated = new Opening(new BlockPos(23, 32, 22), new BlockPos(23, 36, 24), Direction.Axis.X);
        Placement entry = VaultPortalGeometry.place(HUB_FRAME, Direction.SOUTH, rotated, Direction.WEST);

        assertVec(new Vec3(-1, 0, 0), rotate(entry, new Vec3(0, 0, -1)));
        assertVec(new Vec3(23.5 - VaultPortalGeometry.SURFACE_OFFSET, 33.5, 23.5), entry.destination());
    }

    @Test
    void aWideFrameGetsAWindowOverTheWholeFrameWhenTheRoomIsOpen() {
        assertEquals(new WindowSize(5, 6), VaultPortalGeometry.fitEntryWindow(WIDE_HUB_FRAME, START_ROOM, (column, row) -> true));
    }

    @Test
    void theFittedWindowShrinksToTheFreeRoomButNotBelowTheSmallerOpening() {
        // free only straight in front of the 3 wide start-room portal (x 22..24), five blocks up
        RoomCellTest corridor = (column, row) -> {
            BlockPos cell = VaultPortalGeometry.getRoomCell(WIDE_HUB_FRAME, START_ROOM, Direction.NORTH, column, row);
            return cell.getX() >= 22 && cell.getX() <= 24 && row < 5;
        };

        assertEquals(new WindowSize(3, 5), VaultPortalGeometry.fitEntryWindow(WIDE_HUB_FRAME, START_ROOM, corridor));
        assertNull(VaultPortalGeometry.fitEntryWindow(WIDE_HUB_FRAME, START_ROOM, (column, row) -> row < 3));
    }

    @Test
    void roomCellsAreTheBlocksInFrontOfTheStartRoomPortal() {
        assertEquals(new BlockPos(21, 30, 18), VaultPortalGeometry.getRoomCell(WIDE_HUB_FRAME, START_ROOM, Direction.NORTH, 0, 0));
        assertEquals(new BlockPos(25, 35, 18), VaultPortalGeometry.getRoomCell(WIDE_HUB_FRAME, START_ROOM, Direction.NORTH, 4, 5));
    }

    // a player arriving through a fitted window stands in the room, clear of the start-room wall, VH's portal and the
    // window back (whose plane is SURFACE_OFFSET in front of the portal)
    @Test
    void aFittedEntryLandsThePlayerClearOfTheStartRoomWall() {
        Placement entry = VaultPortalGeometry.placeFittedEntry(WIDE_HUB_FRAME, Direction.SOUTH, START_ROOM, Direction.NORTH, new WindowSize(5, 6));
        Vec3 eyeAtOuterColumn = new Vec3(497.4, 200 + 1.62, 500.5 + VaultPortalGeometry.SURFACE_OFFSET - 0.01);
        Vec3 arrivalEye = transform(entry, eyeAtOuterColumn);

        assertEquals(5, entry.width(), EPSILON);
        assertEquals(6, entry.height(), EPSILON);
        assertVec(new Vec3(499.5, 203, 500.5 + VaultPortalGeometry.SURFACE_OFFSET), entry.origin());
        assertVec(new Vec3(23.5, 33, 19.5 - VaultPortalGeometry.ROOM_ARRIVAL_OFFSET), entry.destination());
        assertEquals(30 + 1.62, arrivalEye.y, EPSILON);
        assertEquals(21.4, arrivalEye.x, 1.0E-6);
        assertTrue(arrivalEye.z + 0.3 < 19, "the player's box reaches into the wall at z " + (arrivalEye.z + 0.3));
    }

    private static boolean isAtWindow(Placement placement, AABB box) {
        return VaultPortalGeometry.isBoxAtWindow(
            placement.origin(), placement.axisW(), placement.axisH(), placement.width(), placement.height(), box, 1.5
        );
    }

    private static Vec3 rotate(Placement placement, Vec3 vector) {
        return placement.rotation() == null ? vector : placement.rotation().rotate(vector);
    }

    // what Portal.transformPoint does for an unscaled portal
    private static Vec3 transform(Placement placement, Vec3 point) {
        return rotate(placement, point.subtract(placement.origin())).add(placement.destination());
    }

    private static void assertVec(Vec3 expected, Vec3 actual) {
        assertTrue(expected.distanceTo(actual) < 1.0E-6, "expected " + expected + " but was " + actual);
    }
}
