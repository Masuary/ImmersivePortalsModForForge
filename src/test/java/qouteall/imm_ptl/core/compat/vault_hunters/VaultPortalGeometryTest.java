package qouteall.imm_ptl.core.compat.vault_hunters;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import qouteall.imm_ptl.core.compat.vault_hunters.VaultPortalGeometry.Opening;
import qouteall.imm_ptl.core.compat.vault_hunters.VaultPortalGeometry.Placement;

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
    void theEntryWindowCoversTheWholeFrameJustBeyondTheStartRoomWall() {
        Placement entry = VaultPortalGeometry.placeEntry(HUB_FRAME, Direction.SOUTH, START_ROOM, Direction.NORTH);

        assertEquals(2, entry.width(), EPSILON);
        assertEquals(3, entry.height(), EPSILON);
        assertVec(new Vec3(500, 201.5, 500.5 + VaultPortalGeometry.SURFACE_OFFSET), entry.origin());
        assertVec(new Vec3(23.5, 31.5, 19.5 - VaultPortalGeometry.FAR_FACE_OFFSET), entry.destination());
        assertTrue(entry.destination().z < 19, "the start-room wall must stay behind the far plane");
        assertTrue(entry.destination().z > 18.99, "the far plane must stay at the wall's face");
    }

    @Test
    void aBigFrameGetsAWindowOverTheWholeFrame() {
        Placement entry = VaultPortalGeometry.placeEntry(WIDE_HUB_FRAME, Direction.SOUTH, START_ROOM, Direction.NORTH);

        assertEquals(5, entry.width(), EPSILON);
        assertEquals(6, entry.height(), EPSILON);
        assertVec(new Vec3(499.5, 203, 500.5 + VaultPortalGeometry.SURFACE_OFFSET), entry.origin());
        assertVec(new Vec3(23.5, 33, 19.5 - VaultPortalGeometry.FAR_FACE_OFFSET), entry.destination());
    }

    // the outer columns of a big frame lie in front of the start-room wall: what stands there in the room shows, and a
    // player crossing there lands in the room at the floor
    @Test
    void theOuterColumnsOfABigFrameLieInFrontOfTheStartRoomWall() {
        Placement entry = VaultPortalGeometry.placeEntry(WIDE_HUB_FRAME, Direction.SOUTH, START_ROOM, Direction.NORTH);
        Vec3 arrivalEye = transform(entry, new Vec3(497.4, 200 + 1.62, 500.5 + VaultPortalGeometry.SURFACE_OFFSET - 0.01));

        assertEquals(30 + 1.62, arrivalEye.y, EPSILON);
        assertEquals(21.4, arrivalEye.x, 1.0E-6);
        assertTrue(arrivalEye.z < 19, "the eye is still inside the start-room wall at z " + arrivalEye.z);
        assertEquals(35.6, transform(entry, new Vec3(499.5, 205.6, 500.62)).y, 1.0E-6);
    }

    @Test
    void thePortalFacesItsViewerAndStandsUpright() {
        Placement entry = VaultPortalGeometry.placeEntry(HUB_FRAME, Direction.SOUTH, START_ROOM, Direction.NORTH);

        assertVec(new Vec3(0, 1, 0), entry.axisH());
        assertVec(Vec3.atLowerCornerOf(Direction.SOUTH.getNormal()), entry.axisW().cross(entry.axisH()));
    }

    @Test
    void walkingIntoTheFrameComesOutWalkingIntoTheVault() {
        for (Direction viewerSide : new Direction[]{Direction.SOUTH, Direction.NORTH}) {
            Placement entry = VaultPortalGeometry.placeEntry(HUB_FRAME, viewerSide, START_ROOM, Direction.NORTH);
            Vec3 walking = Vec3.atLowerCornerOf(viewerSide.getOpposite().getNormal());
            assertVec(new Vec3(0, 0, -1), rotate(entry, walking));
            assertVec(new Vec3(0, 1, 0), rotate(entry, new Vec3(0, 1, 0)));
        }
        Placement sideways = VaultPortalGeometry.placeEntry(HUB_FRAME_ALONG_Z, Direction.EAST, START_ROOM, Direction.NORTH);
        assertVec(new Vec3(0, 0, -1), rotate(sideways, new Vec3(-1, 0, 0)));
    }

    @Test
    void aFrameFacingTheVaultDirectionNeedsNoRotation() {
        assertNull(VaultPortalGeometry.placeEntry(HUB_FRAME, Direction.SOUTH, START_ROOM, Direction.NORTH).rotation());
    }

    // entering and leaving again lands at the same spot of the opening, only moved along the frame's normal by the
    // distance between the window planes and the far planes
    @Test
    void theExitWindowUndoesTheEntryWindowAcrossTheOpening() {
        Placement entry = VaultPortalGeometry.placeEntry(HUB_FRAME_ALONG_Z, Direction.WEST, START_ROOM, Direction.NORTH);
        Placement exit = VaultPortalGeometry.placeExit(START_ROOM, Direction.NORTH, HUB_FRAME_ALONG_Z, Direction.WEST);
        Vec3 hubPoint = new Vec3(599.2, 71.3, 99.1);
        Vec3 roundTrip = transform(exit, transform(entry, hubPoint));

        assertVec(new Vec3(1, 0, 0), rotate(exit, rotate(entry, new Vec3(1, 0, 0))));
        assertVec(new Vec3(0, 0, 1), rotate(exit, rotate(entry, new Vec3(0, 0, 1))));
        assertEquals(hubPoint.y, roundTrip.y, 1.0E-6);
        assertEquals(hubPoint.z, roundTrip.z, 1.0E-6);
    }

    @Test
    void theCrossingKeepsTheHeightAboveTheFloor() {
        Placement entry = VaultPortalGeometry.placeEntry(HUB_FRAME, Direction.SOUTH, START_ROOM, Direction.NORTH);
        Vec3 eyeAtFrame = new Vec3(500.0, 200 + 1.62, 500.6);

        assertEquals(30 + 1.62, transform(entry, eyeAtFrame).y, EPSILON);
    }

    @Test
    void floorOpeningsAndSidesOffThePlaneAreRefused() {
        assertThrows(IllegalArgumentException.class,
            () -> new Opening(new BlockPos(0, 64, 0), new BlockPos(2, 64, 2), Direction.Axis.Y));
        assertThrows(IllegalArgumentException.class,
            () -> VaultPortalGeometry.placeEntry(HUB_FRAME, Direction.EAST, START_ROOM, Direction.NORTH));
        assertThrows(IllegalArgumentException.class,
            () -> VaultPortalGeometry.placeExit(START_ROOM, Direction.NORTH, HUB_FRAME, Direction.EAST));
    }

    // Vault Hunters' touch is left to the window only for a player at it, not for one beside it
    @Test
    void aPlayerIsAtTheWindowOnlyInFrontOfItsArea() {
        Placement entry = VaultPortalGeometry.placeEntry(HUB_FRAME, Direction.SOUTH, START_ROOM, Direction.NORTH);
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
        Placement entry = VaultPortalGeometry.placeEntry(HUB_FRAME, Direction.SOUTH, rotated, Direction.WEST);

        assertVec(new Vec3(-1, 0, 0), rotate(entry, new Vec3(0, 0, -1)));
        assertVec(new Vec3(23.5 - VaultPortalGeometry.FAR_FACE_OFFSET, 33.5, 23.5), entry.destination());
    }

    @Test
    void theExitWindowCoversTheWholeStartRoomPortalJustBeyondTheFrameFace() {
        Placement exit = VaultPortalGeometry.placeExit(START_ROOM, Direction.NORTH, HUB_FRAME, Direction.SOUTH);

        assertEquals(3, exit.width(), EPSILON);
        assertEquals(4, exit.height(), EPSILON);
        assertVec(new Vec3(23.5, 32, 19.5 - VaultPortalGeometry.SURFACE_OFFSET), exit.origin());
        assertVec(new Vec3(500, 202, 500.5 + VaultPortalGeometry.FAR_FACE_OFFSET), exit.destination());
        assertTrue(exit.destination().z > 501, "the frame's blocks must stay behind the far plane");
        assertTrue(exit.destination().z < 501.01, "the far plane must stay at the frame's face");
    }

    // through the middle a player comes out in front of the opening, at the floor
    @Test
    void leavingThroughTheMiddleLandsInFrontOfTheOpening() {
        Placement exit = VaultPortalGeometry.placeExit(START_ROOM, Direction.NORTH, HUB_FRAME, Direction.SOUTH);
        Vec3 arrivalEye = transform(exit, new Vec3(23.5, 30 + 1.62, 19.5 - VaultPortalGeometry.SURFACE_OFFSET + 0.01));

        assertEquals(200 + 1.62, arrivalEye.y, EPSILON);
        assertEquals(500, arrivalEye.x, 1.0E-6);
        assertTrue(arrivalEye.z > 501, "the eye is still inside the frame's layer at z " + arrivalEye.z);
    }

    // the outer columns and the top row lie in front of the frame's sides and top: what stands there in the hub shows
    @Test
    void theOuterColumnsLieInFrontOfTheFrameSides() {
        Placement exit = VaultPortalGeometry.placeExit(START_ROOM, Direction.NORTH, HUB_FRAME, Direction.SOUTH);

        Vec3 leftColumn = transform(exit, new Vec3(22.2, 31, 19.37));
        double rightColumn = transform(exit, new Vec3(24.8, 31, 19.37)).x;
        double topRow = transform(exit, new Vec3(23.5, 33.6, 19.37)).y;
        assertTrue(leftColumn.x >= 498 && leftColumn.x < 499, "left column maps to x " + leftColumn.x);
        assertTrue(leftColumn.z > 501, "left column must be in front of the frame, at z " + leftColumn.z);
        assertTrue(rightColumn >= 501 && rightColumn < 502, "right column maps to x " + rightColumn);
        assertTrue(topRow >= 203 && topRow < 204, "top row maps to y " + topRow);
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
