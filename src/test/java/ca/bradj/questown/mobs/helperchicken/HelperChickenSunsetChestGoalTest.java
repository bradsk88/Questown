package ca.bradj.questown.mobs.helperchicken;

import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Pure-function coverage for the SUNSET_AND_MAP chest-spawn picker. The full
 * goal needs a live {@code ServerLevel} + AI tick loop to run; this test
 * targets the slice that determines <em>where</em> the chest goes — the
 * recurring source of "chicken stuck on phase 1" reports (most recently:
 * snow-covered ground returning {@code !isAir()} from every candidate).
 */
class HelperChickenSunsetChestGoalTest {

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    // -- isReplaceable: real BlockState → expected verdict -----------------

    @Test
    void isReplaceable_air_isTrue() {
        Assertions.assertTrue(call(Blocks.AIR.defaultBlockState()));
    }

    @Test
    void isReplaceable_snowLayer_isTrue_regressionGuardForSnowyBiomes() {
        // Reported bug: chest never spawned in snowy biomes because the picker
        // required strict isAir() and snow_layer is not air. snow_layer is in
        // the replaceable-material set, so this must pass.
        Assertions.assertTrue(call(Blocks.SNOW.defaultBlockState()));
    }

    @Test
    void isReplaceable_tallGrassAndShortGrass_areTrue() {
        Assertions.assertTrue(call(Blocks.GRASS.defaultBlockState()));
        Assertions.assertTrue(call(Blocks.TALL_GRASS.defaultBlockState()));
    }

    @Test
    void isReplaceable_fluids_areTrue() {
        Assertions.assertTrue(call(Blocks.WATER.defaultBlockState()));
        Assertions.assertTrue(call(Blocks.LAVA.defaultBlockState()));
    }

    @Test
    void isReplaceable_solids_areFalse() {
        Assertions.assertFalse(call(Blocks.COBBLESTONE.defaultBlockState()));
        Assertions.assertFalse(call(Blocks.GRASS_BLOCK.defaultBlockState()));
        Assertions.assertFalse(call(Blocks.OAK_LOG.defaultBlockState()));
    }

    private static boolean call(BlockState state) {
        return HelperChickenSunsetChestGoal.isReplaceable(state);
    }

    // -- pickChestPos: search algorithm ------------------------------------

    @Test
    void pickChestPos_allClear_picksAtMaxDistance() {
        BlockPos player = new BlockPos(0, 64, 0);
        BlockPos chicken = new BlockPos(2, 64, 2);
        Optional<BlockPos> pick = HelperChickenSunsetChestGoal.pickChestPos(
                player, chicken, 4, anywhere -> true
        );
        Assertions.assertTrue(pick.isPresent());
        // Search starts at the largest distance; the first 8-direction sweep
        // hits a cell at distance 4 and returns it.
        int dx = pick.get().getX() - player.getX();
        int dz = pick.get().getZ() - player.getZ();
        Assertions.assertEquals(4, Math.max(Math.abs(dx), Math.abs(dz)),
                "expected distance-4 pick when everywhere is passable; got " + pick.get());
    }

    @Test
    void pickChestPos_distance4Blocked_fallsBackToCloserCell() {
        BlockPos player = new BlockPos(0, 64, 0);
        Set<BlockPos> blocked = new HashSet<>();
        // Block all 8 surrounding cells at distance 4
        addRing(blocked, player, 4);
        Predicate<BlockPos> passable = p -> !blocked.contains(p);
        Optional<BlockPos> pick = HelperChickenSunsetChestGoal.pickChestPos(
                player, new BlockPos(0, 64, 0), 4, passable
        );
        Assertions.assertTrue(pick.isPresent());
        int dist = Math.max(
                Math.abs(pick.get().getX() - player.getX()),
                Math.abs(pick.get().getZ() - player.getZ())
        );
        Assertions.assertTrue(dist >= 1 && dist <= 3,
                "distance-4 ring blocked → expected fallback to distance 1..3, got " + dist);
    }

    @Test
    void pickChestPos_allRingsBlocked_fallsBackToChickenPos() {
        // Recurring failure mode: chicken in a tightly-walled spot. We must
        // never deadlock the beat — the chicken's own cell is the last resort.
        BlockPos player = new BlockPos(0, 64, 0);
        BlockPos chicken = new BlockPos(10, 64, 10);
        Predicate<BlockPos> passable = p -> p.equals(chicken);
        Optional<BlockPos> pick = HelperChickenSunsetChestGoal.pickChestPos(
                player, chicken, 4, passable
        );
        Assertions.assertEquals(Optional.of(chicken), pick);
    }

    @Test
    void pickChestPos_everythingBlocked_returnsEmpty() {
        BlockPos player = new BlockPos(0, 64, 0);
        Optional<BlockPos> pick = HelperChickenSunsetChestGoal.pickChestPos(
                player, new BlockPos(10, 64, 10), 4, p -> false
        );
        Assertions.assertTrue(pick.isEmpty());
    }

    @Test
    void pickChestPos_snowAtFar_solidsAtNear_picksSnowCell() {
        // Composes the bug-class case end-to-end: the picker uses the
        // passability predicate that already accepts snow. We model that by
        // marking only the snow cell as passable.
        BlockPos player = new BlockPos(0, 64, 0);
        BlockPos snowCell = player.north(4);
        Predicate<BlockPos> passable = p -> p.equals(snowCell);
        Optional<BlockPos> pick = HelperChickenSunsetChestGoal.pickChestPos(
                player, new BlockPos(99, 64, 99), 4, passable
        );
        Assertions.assertEquals(Optional.of(snowCell), pick);
    }

    private static void addRing(Set<BlockPos> sink, BlockPos center, int distance) {
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (dx == 0 && dz == 0) {
                    continue;
                }
                sink.add(center.offset(dx * distance, 0, dz * distance));
            }
        }
    }

    // End-to-end goal-tick coverage (canUse → start → peck countdown →
    // setBlockAndUpdate + map+axe insert + persist chickenSunsetChestSpawned)
    // is provided by the {@code F2_chest_spawn_after_campfire} scenario in
    // {@code ChickenArcBlueprintRegistry}, which runs against a live
    // ServerLevel via {@code /_qtdev testall} or {@code /_qtdev test}.
}
