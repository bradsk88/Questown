package ca.bradj.questown.commands.test;

import ca.bradj.questown.mobs.helperchicken.HelperChickenEntity;
import ca.bradj.questown.mobs.visitor.VisitorMobEntity;
import ca.bradj.questown.town.HelperChickenRotationDetector;
import ca.bradj.questown.town.entity.TownFlagBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;

/**
 * Shared arena-setup primitives lifted from {@link TestExecutor} so both the
 * jobs track and the chicken-arc track compose the same destroy-and-flatten
 * loop against one record of options.
 *
 * <p>The jobs track passes {@link PreparerOptions#jobsTrackDefaults()} to keep
 * its existing observable behavior byte-for-byte. The chicken track opts into
 * a wider arena, a {@link HelperChickenEntity} kill-sweep, a fake-player
 * inventory clear, and a {@link HelperChickenRotationDetector} retry-counter
 * clear — each of those is a no-op by default so the jobs track cannot
 * regress.
 */
public final class TestArenaPreparer {

    /**
     * Knobs for one preparer invocation. Jobs track uses
     * {@link #jobsTrackDefaults()}; chicken track builds a custom value with
     * opt-ins enabled.
     *
     * @param halfWidth                         half-width of the square arena in
     *                                          blocks (so a value of 7 produces
     *                                          a 15x15 area)
     * @param killHelperChickens                when true, kill every
     *                                          {@link HelperChickenEntity} inside
     *                                          the arena bounds
     * @param clearFakePlayerInventory          when true, clear the fake player's
     *                                          inventory (only has effect if
     *                                          {@code fakePlayer} is non-null)
     * @param clearRotationDetectorRetryCounters when true, clear
     *                                          {@link HelperChickenRotationDetector}'s
     *                                          static retry counter map
     */
    public record PreparerOptions(
            int halfWidth,
            boolean killHelperChickens,
            boolean clearFakePlayerInventory,
            boolean clearRotationDetectorRetryCounters
    ) {
        public static PreparerOptions jobsTrackDefaults() {
            return new PreparerOptions(7, false, false, false);
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
            int helperChickensKilled,
            @Nullable BlockPos newOrigin
    ) {}

    private TestArenaPreparer() {
    }

    /**
     * Destroy nearby flags, kill stray visitors, optionally kill helper chickens,
     * optionally clear the fake player's inventory, optionally clear the
     * rotation-detector retry counters.
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
        int chickens = 0;
        if (options.killHelperChickens()) {
            // Chickens wander; prior-scenario survivors routinely drift 20+ blocks
            // from origin. Use a wide sweep so later scenarios start clean, and
            // additionally discard any entity bound to this origin via ownerFlagPos
            // so wanderers outside the bounds still get cleaned up.
            AABB wide = new AABB(
                    origin.offset(-128, -16, -128),
                    origin.offset(128, 32, 128)
            );
            for (HelperChickenEntity e : level.getEntitiesOfClass(HelperChickenEntity.class, wide)) {
                e.discard();
                chickens++;
            }
        }
        if (options.clearFakePlayerInventory() && fakePlayer != null) {
            fakePlayer.getInventory().clearContent();
        }
        if (options.clearRotationDetectorRetryCounters()) {
            HelperChickenRotationDetector.clearRetryCounters();
        }
        return new PreparerResult(destroyed, stray, chickens, firstFlagPos);
    }

    /**
     * Flatten a square arena around {@code origin}. Matches {@link TestExecutor}'s
     * existing flatten semantics: destroy all non-ground blocks above ground level,
     * set the ground layer to cobblestone. {@link PreparerOptions#halfWidth()}
     * controls the arena size.
     */
    public static void flatten(
            ServerLevel level,
            BlockPos origin,
            PreparerOptions options
    ) {
        int halfWidth = options.halfWidth();
        for (int x = -halfWidth; x <= halfWidth; x++) {
            for (int z = -halfWidth; z <= halfWidth; z++) {
                for (int y = 4; y >= 0; y--) {
                    BlockPos pos = origin.offset(x, y, z);
                    level.destroyBlock(pos, false);
                }
                BlockPos groundPos = origin.offset(x, -1, z);
                level.setBlockAndUpdate(groundPos, Blocks.COBBLESTONE.defaultBlockState());
            }
        }
    }

    private static AABB strayAreaAround(BlockPos origin, int halfWidth) {
        int stray = halfWidth + 13; // retain legacy +20/origin=+13/half=7 relationship
        return new AABB(
                origin.offset(-stray, -5, -stray),
                origin.offset(stray, 10, stray)
        );
    }
}
