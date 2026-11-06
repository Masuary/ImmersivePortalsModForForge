package qouteall.imm_ptl.core.compat.vault_hunters;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Matrix3f;
import com.mojang.math.Matrix4f;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.client.event.DrawSelectionEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.client.model.data.EmptyModelData;
import qouteall.imm_ptl.core.ClientWorldLoader;
import qouteall.imm_ptl.core.render.PortalRenderer;
import qouteall.imm_ptl.core.render.context_management.PortalRendering;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.TreeSet;

/**
 * A vault window ends just beyond the face of the wall or frame its far opening sits in, so the blocks standing in
 * front of that wall or frame (an arch around a hub frame, the start room's walls and ceiling) touch its far plane.
 * Minecraft's chunk meshes leave out a block face when its neighbour hides it, and the faces of these blocks towards
 * the window touch that wall or frame, so through the window they would look hollow. This draws those left-out faces
 * over the window: the block model's own quads for that side (full cubes, slabs, stairs alike), carried through the
 * portal, clipped to the window and lit with the far side's light. It also keeps the selection outline off the Vault
 * Hunters portal blocks behind windows.
 */
public final class VaultWindowRendering {
    private static final double RENDER_DISTANCE = 32;
    // just in front of the window surface, so the drawn faces cover the window's view of the cut blocks
    private static final double SURFACE_LIFT = 0.002;
    // the drawn faces and the rest of the block (seen through the window) share no vertices; overlapping them a little
    // closes the hairline cracks the sky would show through
    private static final double EDGE_OVERLAP = 0.004;
    // the centre of Vault Hunters' portal block lies 0.255 behind its window; the frame and the floor are further away
    private static final double WINDOW_HIGHLIGHT_DISTANCE = 0.5;
    private static final Random MODEL_RANDOM = new Random();
    // block boundaries that nearly coincide would leave hairline cells
    private static final double MINIMUM_CELL_SIZE = 1.0E-3;
    // BakedQuad vertex data in the BLOCK format: x, y, z, colour, u, v, light, normal
    private static final int VERTEX_STRIDE = 8;
    private static final Direction[] LIGHT_SOURCE_SIDES = {
        Direction.UP, Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST, Direction.DOWN
    };

    private VaultWindowRendering() {
    }

    // Vault Hunters' portal block right behind a window would get the selection outline, which shows through the window
    public static void onHighlightBlock(DrawSelectionEvent.HighlightBlock event) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) {
            return;
        }
        Vec3 blockCentre = Vec3.atCenterOf(event.getTarget().getBlockPos());
        AABB centreBox = new AABB(blockCentre, blockCentre).inflate(0.01);
        for (VaultPortal portal : level.getEntitiesOfClass(VaultPortal.class, centreBox.inflate(1))) {
            if (portal.isBoxAtWindow(centreBox, WINDOW_HIGHLIGHT_DISTANCE)) {
                event.setCanceled(true);
                return;
            }
        }
    }

    // after the translucent layer, so after Immersive Portals has drawn the portals (it does so before that layer)
    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS || level == null || PortalRendering.isRendering()) {
            return;
        }
        Vec3 cameraPos = event.getCamera().getPosition();
        MultiBufferSource.BufferSource buffers = minecraft.renderBuffers().bufferSource();
        RenderType renderType = RenderType.entityCutout(InventoryMenu.BLOCK_ATLAS);
        boolean drewAny = false;
        double renderDistance = Math.min(RENDER_DISTANCE, PortalRenderer.getRenderRange());
        for (Entity entity : level.entitiesForRendering()) {
            if (entity instanceof VaultPortal portal
                && portal.isPortalValid()
                && portal.isInFrontOfPortal(cameraPos)
                && portal.getDistanceToNearestPointInPortal(cameraPos) <= renderDistance) {
                // null for a vault level the client already dropped while the server has not removed its window yet
                ClientLevel farSide = ClientWorldLoader.getOptionalWorld(portal.dimensionTo);
                if (farSide != null) {
                    drawCutFaces(event.getPoseStack(), buffers.getBuffer(renderType), portal, farSide, cameraPos);
                    drewAny = true;
                }
            }
        }
        if (drewAny) {
            buffers.endBatch(renderType);
        }
    }

    private static void drawCutFaces(PoseStack poseStack, VertexConsumer consumer, VaultPortal portal, ClientLevel farSide, Vec3 cameraPos) {
        Vec3 normal = portal.axisW.cross(portal.axisH);
        Vec3 farContentDirection = portal.transformLocalVec(normal.scale(-1));
        Direction faceTowardsViewer = Direction.getNearest(-farContentDirection.x, -farContentDirection.y, -farContentDirection.z);
        poseStack.pushPose();
        poseStack.translate(-cameraPos.x, -cameraPos.y, -cameraPos.z);
        Matrix4f pose = poseStack.last().pose();
        Matrix3f normalMatrix = poseStack.last().normal();
        Minecraft minecraft = Minecraft.getInstance();
        for (BlockPos farPos : getBlocksAtFarPlane(portal, farContentDirection)) {
            BlockState state = farSide.getBlockState(farPos);
            if (state.isAir()) {
                continue;
            }
            BakedModel model = minecraft.getBlockRenderer().getBlockModel(state);
            // the same texture variant as the chunk mesh, as vanilla's block renderer seeds it
            MODEL_RANDOM.setSeed(state.getSeed(farPos));
            List<BakedQuad> quads = model.getQuads(state, faceTowardsViewer, MODEL_RANDOM, EmptyModelData.INSTANCE);
            int light = getLight(farSide, farPos, faceTowardsViewer);
            for (BakedQuad quad : quads) {
                drawQuad(consumer, pose, normalMatrix, portal, normal, quad, state, farSide, farPos, light);
            }
        }
        poseStack.popPose();
    }

    // the far side blocks just beyond the far plane that the window lies over, found along their block grid
    private static Set<BlockPos> getBlocksAtFarPlane(VaultPortal portal, Vec3 farContentDirection) {
        Vec3 farOrigin = portal.transformPoint(portal.getOriginPos());
        Vec3 farAxisW = portal.transformLocalVec(portal.axisW);
        Vec3 farAxisH = portal.transformLocalVec(portal.axisH);
        double[] uCuts = getGridCuts(farOrigin, farAxisW, -portal.width / 2, portal.width / 2);
        double[] vCuts = getGridCuts(farOrigin, farAxisH, -portal.height / 2, portal.height / 2);
        Set<BlockPos> blocks = new LinkedHashSet<>();
        for (int i = 0; i + 1 < uCuts.length; i++) {
            for (int j = 0; j + 1 < vCuts.length; j++) {
                if (uCuts[i + 1] - uCuts[i] < MINIMUM_CELL_SIZE || vCuts[j + 1] - vCuts[j] < MINIMUM_CELL_SIZE) {
                    continue;
                }
                Vec3 cellCentre = localToWorld(portal, (uCuts[i] + uCuts[i + 1]) / 2, (vCuts[j] + vCuts[j + 1]) / 2);
                blocks.add(new BlockPos(portal.transformPoint(cellCentre).add(farContentDirection.scale(0.5))));
            }
        }
        return blocks;
    }

    // local coordinates between from and to where the far side coordinate along the axis crosses a whole block boundary
    private static double[] getGridCuts(Vec3 farOrigin, Vec3 farAxis, double from, double to) {
        double base = farOrigin.dot(farAxis);
        double sign = Math.signum(farAxis.x + farAxis.y + farAxis.z);
        // the far side coordinate along the axis is (base + u) * sign; block boundaries are where that is an integer
        double farFrom = (base + from) * sign;
        double farTo = (base + to) * sign;
        TreeSet<Double> cuts = new TreeSet<>();
        cuts.add(from);
        cuts.add(to);
        for (double boundary = Math.floor(Math.min(farFrom, farTo)) + 1; boundary < Math.max(farFrom, farTo); boundary += 1) {
            double cut = boundary * sign - base;
            if (Math.abs(cut - from) >= MINIMUM_CELL_SIZE && Math.abs(cut - to) >= MINIMUM_CELL_SIZE) {
                cuts.add(cut);
            }
        }
        return cuts.stream().mapToDouble(Double::doubleValue).toArray();
    }

    // one model quad, carried from the far side block to this side, clipped to the window and drawn just in front of it
    private static void drawQuad(
        VertexConsumer consumer, Matrix4f pose, Matrix3f normalMatrix, VaultPortal portal, Vec3 normal, BakedQuad quad,
        BlockState state, ClientLevel farSide, BlockPos farPos, int light
    ) {
        int[] data = quad.getVertices();
        List<WindowVertex> polygon = new ArrayList<>(4);
        for (int vertex = 0; vertex < 4; vertex++) {
            int offset = vertex * VERTEX_STRIDE;
            Vec3 farPoint = new Vec3(
                farPos.getX() + Float.intBitsToFloat(data[offset]),
                farPos.getY() + Float.intBitsToFloat(data[offset + 1]),
                farPos.getZ() + Float.intBitsToFloat(data[offset + 2])
            );
            Vec3 relative = portal.inverseTransformPoint(farPoint).subtract(portal.getOriginPos());
            polygon.add(new WindowVertex(
                relative.dot(portal.axisW), relative.dot(portal.axisH),
                Float.intBitsToFloat(data[offset + 4]), Float.intBitsToFloat(data[offset + 5])
            ));
        }
        polygon = clip(expand(polygon), -portal.width / 2, portal.width / 2, -portal.height / 2, portal.height / 2);
        if (polygon.size() < 3) {
            return;
        }
        int tint = quad.isTinted() ? Minecraft.getInstance().getBlockColors().getColor(state, farSide, farPos, quad.getTintIndex()) : 0xFFFFFF;
        float shade = quad.isShade() ? farSide.getShade(quad.getDirection(), true) : 1F;
        float red = ((tint >> 16) & 0xFF) / 255F * shade;
        float green = ((tint >> 8) & 0xFF) / 255F * shade;
        float blue = (tint & 0xFF) / 255F * shade;
        Vec3 lift = normal.scale(SURFACE_LIFT);
        // a fan of quads; a triangle is a quad with its last corner repeated
        for (int index = 1; index + 1 < polygon.size(); index += 2) {
            WindowVertex third = polygon.get(index + 1);
            WindowVertex fourth = index + 2 < polygon.size() ? polygon.get(index + 2) : third;
            for (WindowVertex corner : List.of(polygon.get(0), polygon.get(index), third, fourth)) {
                Vec3 pos = localToWorld(portal, corner.u, corner.v).add(lift);
                consumer.vertex(pose, (float) pos.x, (float) pos.y, (float) pos.z)
                    .color(red, green, blue, 1F)
                    .uv(corner.textureU, corner.textureV)
                    .overlayCoords(OverlayTexture.NO_OVERLAY)
                    .uv2(light)
                    .normal(normalMatrix, (float) normal.x, (float) normal.y, (float) normal.z)
                    .endVertex();
            }
        }
    }

    // moves every corner EDGE_OVERLAP outwards; the texture stretches by a fraction of a pixel
    private static List<WindowVertex> expand(List<WindowVertex> polygon) {
        double centreU = polygon.stream().mapToDouble(WindowVertex::u).average().orElseThrow();
        double centreV = polygon.stream().mapToDouble(WindowVertex::v).average().orElseThrow();
        List<WindowVertex> expanded = new ArrayList<>(polygon.size());
        for (WindowVertex vertex : polygon) {
            expanded.add(new WindowVertex(
                vertex.u + Math.signum(vertex.u - centreU) * EDGE_OVERLAP,
                vertex.v + Math.signum(vertex.v - centreV) * EDGE_OVERLAP,
                vertex.textureU, vertex.textureV
            ));
        }
        return expanded;
    }

    // Sutherland-Hodgman against the window rectangle, interpolating the texture coordinates
    private static List<WindowVertex> clip(List<WindowVertex> polygon, double minU, double maxU, double minV, double maxV) {
        polygon = clipEdge(polygon, vertex -> vertex.u - minU);
        polygon = clipEdge(polygon, vertex -> maxU - vertex.u);
        polygon = clipEdge(polygon, vertex -> vertex.v - minV);
        return clipEdge(polygon, vertex -> maxV - vertex.v);
    }

    private static List<WindowVertex> clipEdge(List<WindowVertex> polygon, InsideDistance insideDistance) {
        List<WindowVertex> result = new ArrayList<>(polygon.size() + 2);
        for (int index = 0; index < polygon.size(); index++) {
            WindowVertex current = polygon.get(index);
            WindowVertex next = polygon.get((index + 1) % polygon.size());
            double currentDistance = insideDistance.of(current);
            double nextDistance = insideDistance.of(next);
            if (currentDistance >= 0) {
                result.add(current);
            }
            if ((currentDistance >= 0) != (nextDistance >= 0)) {
                result.add(current.towards(next, currentDistance / (currentDistance - nextDistance)));
            }
        }
        return result;
    }

    // the light the face gets on the far side: from the open side in front of it, else from the brightest open neighbour
    private static int getLight(ClientLevel farSide, BlockPos pos, Direction face) {
        BlockPos front = pos.relative(face);
        if (!farSide.getBlockState(front).isSolidRender(farSide, front)) {
            return LevelRenderer.getLightColor(farSide, front);
        }
        int brightest = 0;
        for (Direction side : LIGHT_SOURCE_SIDES) {
            BlockPos neighbour = pos.relative(side);
            if (!farSide.getBlockState(neighbour).isSolidRender(farSide, neighbour)) {
                brightest = Math.max(brightest, LevelRenderer.getLightColor(farSide, neighbour));
            }
        }
        return brightest;
    }

    private static Vec3 localToWorld(VaultPortal portal, double u, double v) {
        return portal.getOriginPos().add(portal.axisW.scale(u)).add(portal.axisH.scale(v));
    }

    @FunctionalInterface
    private interface InsideDistance {
        double of(WindowVertex vertex);
    }

    private record WindowVertex(double u, double v, float textureU, float textureV) {
        WindowVertex towards(WindowVertex other, double fraction) {
            return new WindowVertex(
                u + (other.u - u) * fraction, v + (other.v - v) * fraction,
                (float) (textureU + (other.textureU - textureU) * fraction),
                (float) (textureV + (other.textureV - textureV) * fraction)
            );
        }
    }
}
