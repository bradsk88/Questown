package ca.bradj.questown.commands.test;

import ca.bradj.questown.mobs.visitor.VisitorMobEntity;
import ca.bradj.questown.town.entity.TownFlagBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;

/**
 * Arena setup shared by the jobs track and the village scenarios: the same destroy-and-flatten
 * loop against one record of options.
 */
public final class TestArenaPreparer {

    /**
     * Knobs for one preparer invocation. Jobs track uses
     * {@link #jobsTrackDefaults()}; the village scenarios build a wider one.
     *
     * @param halfWidth                         half-width of the square arena in
     *                                          blocks (so a value of 7 produces
     *                                          a 15x15 area)
     * @param clearFakePlayerInventory          when true, clear the fake player's
     *                                          inventory (only has effect if
     *                                          {@code fakePlayer} is non-null)
     */
    public record PreparerOptions(
            int halfWidth,
            boolean clearFakePlayerInventory
    ) {
        public static PreparerOptions jobsTrackDefaults() {
            return new PreparerOptions(7, false);
        }
    }

    /**
     * Result of one preparer invocation. {@code newOrigin} is non-null when an
     * existing flag was found during the destroy sweep and promoted to the new
     * origin — callers should thread it back into their own origin field.
     */
    public record PreparerResult(
            int flagsDestroyed,
            int strayVisitorsKilled,
            @Nullable BlockPos newOrigin
    ) {}

    private TestArenaPreparer() {
    }

    /**
     * Destroy nearby flags, kill stray visitors, optionally clear the fake
     * player's inventory.
     *
     * <p>This mirrors {@link TestExecutor}'s {@code destroyNearbyFlags} phase
     * with opt-in extensions. Does <em>not</em> flatten — see {@link #flatten}.
     */
    public static PreparerResult destroyNearbyFlags(
            ServerLevel level,
            BlockPos origin,
            PreparerOptions options,
            @Nullable ServerPlayer fakePlayer
    ) {
        int halfWidth = options.halfWidth();
        int destroyed = 0;
        BlockPos firstFlagPos = null;
        for (int x = -halfWidth; x <= halfWidth; x++) {
            for (int z = -halfWidth; z <= halfWidth; z++) {
                for (int y = -1; y <= 4; y++) {
                    BlockPos pos = origin.offset(x, y, z);
                    BlockEntity be = level.getBlockEntity(pos);
                    if (!(be instanceof TownFlagBlockEntity tf)) {
                        continue;
                    }
                    if (firstFlagPos == null) {
                        firstFlagPos = pos;
                    }
                    tf.getVillagerHandle().entities().forEach(LivingEntity::kill);
                    level.destroyBlock(pos, false);
                    destroyed++;
                }
            }
        }
        AABB area = strayAreaAround(origin, halfWidth);
        int stray = 0;
        for (VisitorMobEntity e : level.getEntitiesOfClass(VisitorMobEntity.class, area)) {
            e.kill();
            stray++;
        }
        if (options.clearFakePlayerInventory() && fakePlayer != null) {
            fakePlayer.getInventory().clearContent();
        }
        return new PreparerResult(destroyed, stray, firstFlagPos);
    }

    /**
     * Rectangular clear region in origin-relative offsets: {@code [minX..maxX] x [minZ..maxZ]}
     * cleared from {@code topY} down to the floor, with the ground layer (y=-1) set to cobblestone.
     */
    public record ClearRegion(int minX, int maxX, int minZ, int maxZ, int topY) {}

    /**
     * Flatten a square arena around {@code origin}. Matches {@link TestExecutor}'s
     * existing flatten semantics: destroy all non-ground blocks above ground level,
     * set the ground layer to cobblestone. {@link PreparerOptions#halfWidth()}
     * controls the arena size; the vertical clear matches the legacy 5-high box.
     */
    public static void flatten(
            ServerLevel level,
            BlockPos origin,
            PreparerOptions options
    ) {
        int halfWidth = options.halfWidth();
        flatten(level, origin, new ClearRegion(-halfWidth, halfWidth, -halfWidth, halfWidth, 4));
    }

    /**
     * Flatten an explicit {@link ClearRegion}. Used by the jobs track to clear the full build
     * volume (see {@link #buildVolume}) so prior-scenario residue can't survive into a taller or
     * wider blueprint — e.g. an arborist farm whose grown tree needs vertical clearance.
     */
    public static void flatten(
            ServerLevel level,
            BlockPos origin,
            ClearRegion region
    ) {
        for (int x = region.minX(); x <= region.maxX(); x++) {
            for (int z = region.minZ(); z <= region.maxZ(); z++) {
                // Clear up to the natural surface too: where the ground stands above the fixed
                // clear height (a plains hill), the leftover terrain roofs the arena and the
                // fixture sits in a dark cave - crops placed there pop off at once (raw light 0,
                // no sky).
                int surface = level.getHeight(Heightmap.Types.WORLD_SURFACE, origin.getX() + x, origin.getZ() + z) - origin.getY();
                int top = Math.max(region.topY(), surface);
                for (int y = top; y >= 0; y--) {
                    level.destroyBlock(origin.offset(x, y, z), false);
                }
                level.setBlockAndUpdate(origin.offset(x, -1, z), Blocks.COBBLESTONE.defaultBlockState());
            }
        }
    }

    /**
     * The clear region covering a scenario's full build volume: the base {@code halfWidth} square
     * unioned with the blueprint's block bounding box, cleared up through the tallest blueprint
     * block plus {@code headroom}. Deriving from the blueprint keeps "fully clear the build volume"
     * true by construction even when a blueprint outgrows the base square (the arborist farm does).
     */
    public static ClearRegion buildVolume(
            int halfWidth,
            Iterable<TestBlueprint.BlockPlacement> blocks,
            int headroom
    ) {
        int minX = -halfWidth, maxX = halfWidth, minZ = -halfWidth, maxZ = halfWidth, maxY = 4;
        for (TestBlueprint.BlockPlacement bp : blocks) {
            BlockPos o = bp.offset();
            minX = Math.min(minX, o.getX());
            maxX = Math.max(maxX, o.getX());
            minZ = Math.min(minZ, o.getZ());
            maxZ = Math.max(maxZ, o.getZ());
            maxY = Math.max(maxY, o.getY());
        }
        return new ClearRegion(minX, maxX, minZ, maxZ, maxY + headroom);
    }

    private static AABB strayAreaAround(BlockPos origin, int halfWidth) {
        int stray = halfWidth + 13; // retain legacy +20/origin=+13/half=7 relationship
        return new AABB(origin.offset(-stray, -5, -stray)).minmax(new AABB(origin.offset(stray, 10, stray)));
    }
}
