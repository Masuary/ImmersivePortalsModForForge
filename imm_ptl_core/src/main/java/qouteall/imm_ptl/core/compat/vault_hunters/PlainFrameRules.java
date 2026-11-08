package qouteall.imm_ptl.core.compat.vault_hunters;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * Plain frames (owner 2026-11-08): a player who may break a lit hub frame can switch its vault window off by sneaking
 * and using the configured toggle item on it, so everyone sees and uses Vault Hunters' own portal there. Pure, without
 * Vault Hunters classes, so it is unit tested; {@link PlainFrames} acts on it.
 */
public final class PlainFrameRules {
    public static final String DEFAULT_TOGGLE_ITEM = "minecraft:glowstone_dust";
    // holding the use key repeats it every 4 ticks; a use this soon after the last one does not switch again
    public static final int TOGGLE_COOLDOWN_TICKS = 10;
    // the client sends its use of the held items in the air in the same tick as the use on the frame
    private static final int ITEM_USE_AFTER_TOGGLE_TICKS = 1;
    private static final String TAG_PREFIX = "#";

    public enum ToggleItemKind {
        NONE,
        ITEM,
        TAG
    }

    public record ToggleItem(ToggleItemKind kind, @Nullable ResourceLocation id) {
    }

    private static volatile ToggleItem toggleItem = parseToggleItem(DEFAULT_TOGGLE_ITEM);

    private PlainFrameRules() {
    }

    /**
     * An item id ({@code minecraft:glowstone_dust}), an item tag ({@code #forge:dusts/glowstone}) or an empty value,
     * which turns the toggle off.
     */
    public static ToggleItem parseToggleItem(String value) {
        String trimmed = value.strip();
        if (trimmed.isEmpty()) {
            return new ToggleItem(ToggleItemKind.NONE, null);
        }
        boolean isTag = trimmed.startsWith(TAG_PREFIX);
        String id = isTag ? trimmed.substring(TAG_PREFIX.length()) : trimmed;
        if (id.isEmpty() || !ResourceLocation.isValidResourceLocation(id)) {
            throw new IllegalArgumentException("vaultWindowToggleItem is not an item id, an item tag (#namespace:path) or empty: " + value);
        }
        return new ToggleItem(isTag ? ToggleItemKind.TAG : ToggleItemKind.ITEM, new ResourceLocation(id));
    }

    public static boolean isWellFormedToggleItem(String value) {
        try {
            parseToggleItem(value);
            return true;
        }
        catch (IllegalArgumentException exception) {
            return false;
        }
    }

    public static void configure(String configuredToggleItem) {
        toggleItem = parseToggleItem(configuredToggleItem);
    }

    public static ToggleItem getToggleItem() {
        return toggleItem;
    }

    /**
     * The hand whose use switches the frame: the main hand when it holds the toggle item (the client tries it first),
     * else the off hand.
     */
    @Nullable
    public static InteractionHand getToggleHand(boolean mainHandHoldsToggleItem, boolean offHandHoldsToggleItem) {
        if (mainHandHoldsToggleItem) {
            return InteractionHand.MAIN_HAND;
        }
        return offHandHoldsToggleItem ? InteractionHand.OFF_HAND : null;
    }

    /**
     * The client sends the off hand's use right after a main-hand use the server consumed without telling it; that use
     * belongs to the toggle and must not place or use the off-hand item.
     */
    public static boolean isOffHandUseAfterToggle(InteractionHand usedHand, InteractionHand toggleHand) {
        return usedHand == InteractionHand.OFF_HAND && toggleHand == InteractionHand.MAIN_HAND;
    }

    public static boolean isToggleCoolingDown(long ticksSinceLastAttempt) {
        return ticksSinceLastAttempt >= 0 && ticksSinceLastAttempt < TOGGLE_COOLDOWN_TICKS;
    }

    /**
     * The held items' own use (an ender pearl, food) that follows a use on the frame belongs to the switch and must not
     * happen.
     */
    public static boolean isItemUseOfToggle(long ticksSinceLastAttempt) {
        return ticksSinceLastAttempt >= 0 && ticksSinceLastAttempt <= ITEM_USE_AFTER_TOGGLE_TICKS;
    }

    public static boolean contains(BlockPos min, BlockPos max, BlockPos pos) {
        return pos.getX() >= min.getX() && pos.getX() <= max.getX()
            && pos.getY() >= min.getY() && pos.getY() <= max.getY()
            && pos.getZ() >= min.getZ() && pos.getZ() <= max.getZ();
    }

    /**
     * The frame blocks around a wall opening one block thick along {@code normalAxis}: the columns left and right of it
     * and the rows below and above it, without the corners, which Vault Hunters' frames do not need.
     */
    public static List<BlockPos> getFrameRing(BlockPos min, BlockPos max, Direction.Axis normalAxis) {
        if (normalAxis == Direction.Axis.Y || min.get(normalAxis) != max.get(normalAxis)) {
            throw new IllegalArgumentException("Not a wall opening one block thick along %s: %s..%s".formatted(normalAxis, min, max));
        }
        Direction.Axis widthAxis = normalAxis == Direction.Axis.X ? Direction.Axis.Z : Direction.Axis.X;
        Direction widthStep = Direction.fromAxisAndDirection(widthAxis, Direction.AxisDirection.POSITIVE);
        List<BlockPos> ring = new ArrayList<>();
        for (int y = min.getY(); y <= max.getY(); y++) {
            ring.add(atHeight(min, y).relative(widthStep.getOpposite()));
            ring.add(atHeight(max, y).relative(widthStep));
        }
        for (BlockPos bottom = min; bottom.get(widthAxis) <= max.get(widthAxis); bottom = bottom.relative(widthStep)) {
            ring.add(bottom.below());
            ring.add(atHeight(bottom, max.getY()).above());
        }
        return ring;
    }

    private static BlockPos atHeight(BlockPos pos, int y) {
        return new BlockPos(pos.getX(), y, pos.getZ());
    }
}
