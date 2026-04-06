package ca.bradj.questown.jobs.special;

import ca.bradj.questown.integration.jobs.WarpTickEvent;
import ca.bradj.questown.world.TestWorldAccess;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

class GrowCropsWarpRuleTest {

    private static final BlockPos POS_1 = new BlockPos(10, 64, 10);
    private static final BlockPos POS_2 = new BlockPos(11, 64, 10);
    private static final BlockPos POS_3 = new BlockPos(12, 64, 10);

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    private static WarpTickEvent makeEvent(
            TestWorldAccess world,
            long tickDelta,
            List<BlockPos> positions
    ) {
        return new WarpTickEvent(world, 1000L, tickDelta, () -> positions);
    }

    // ========== Deterministic path (tickDelta >= 1366) ==========

    @Test
    void shouldGrowCrop_whenTickDeltaIsLarge() {
        TestWorldAccess world = new TestWorldAccess()
                .withBlockProperty(POS_1, "age", 0, 7);

        // 4096 * 3 / 4096 = 3 growths per block
        WarpTickEvent event = makeEvent(world, 4096, List.of(POS_1));

        GrowCropsWarpRule rule = new GrowCropsWarpRule();
        Boolean result = rule.onWarpTick(true, event);

        Assertions.assertEquals(3, world.getPropertyValue(POS_1, "age"));
        Assertions.assertTrue(result);
    }

    @Test
    void shouldCapAtMaxAge_whenGrowthExceedsMax() {
        TestWorldAccess world = new TestWorldAccess()
                .withBlockProperty(POS_1, "age", 6, 7);

        // 8192 * 3 / 4096 = 6 growths, but 6 + 6 = 12 → capped at 7
        WarpTickEvent event = makeEvent(world, 8192, List.of(POS_1));

        new GrowCropsWarpRule().onWarpTick(true, event);

        Assertions.assertEquals(7, world.getPropertyValue(POS_1, "age"));
    }

    @Test
    void shouldNotGrow_whenAlreadyAtMaxAge() {
        TestWorldAccess world = new TestWorldAccess()
                .withBlockProperty(POS_1, "age", 7, 7);

        WarpTickEvent event = makeEvent(world, 4096, List.of(POS_1));

        new GrowCropsWarpRule().onWarpTick(true, event);

        Assertions.assertEquals(7, world.getPropertyValue(POS_1, "age"));
    }

    @Test
    void shouldNotGrow_whenBlockHasNoAgeProperty() {
        TestWorldAccess world = new TestWorldAccess();

        WarpTickEvent event = makeEvent(world, 4096, List.of(POS_1));

        new GrowCropsWarpRule().onWarpTick(true, event);

        Assertions.assertEquals(-1, world.getPropertyValue(POS_1, "age"));
    }

    @Test
    void shouldGrowMultipleCrops_independently() {
        TestWorldAccess world = new TestWorldAccess()
                .withBlockProperty(POS_1, "age", 0, 7)
                .withBlockProperty(POS_2, "age", 4, 7)
                .withBlockProperty(POS_3, "age", 6, 7);

        // 4096 * 3 / 4096 = 3 growths per block
        WarpTickEvent event = makeEvent(world, 4096, List.of(POS_1, POS_2, POS_3));

        new GrowCropsWarpRule().onWarpTick(true, event);

        Assertions.assertEquals(3, world.getPropertyValue(POS_1, "age"));
        Assertions.assertEquals(7, world.getPropertyValue(POS_2, "age")); // 4+3=7
        Assertions.assertEquals(7, world.getPropertyValue(POS_3, "age")); // 6+3=9 → capped at 7
    }

    // ========== Population-based path (tickDelta < 1366) ==========

    @Test
    void shouldGrowGuaranteedBlocks_whenPopulationIsLarge() {
        BlockPos pos4 = new BlockPos(13, 64, 10);
        BlockPos pos5 = new BlockPos(14, 64, 10);
        BlockPos pos6 = new BlockPos(15, 64, 10);

        TestWorldAccess world = new TestWorldAccess()
                .withBlockProperty(POS_1, "age", 0, 7)
                .withBlockProperty(POS_2, "age", 0, 7)
                .withBlockProperty(POS_3, "age", 0, 7)
                .withBlockProperty(pos4, "age", 0, 7)
                .withBlockProperty(pos5, "age", 0, 7)
                .withBlockProperty(pos6, "age", 0, 7);

        // tickDelta=1000 → deterministicGrowths = 1000*3/4096 = 0
        // perBlockChance = 1000*3/4096.0 ≈ 0.7324
        // totalExpected = 0.7324 * 6 ≈ 4.39
        // guaranteed = 4, remainder ≈ 0.39 (probabilistic extra)
        List<BlockPos> allPositions = List.of(POS_1, POS_2, POS_3, pos4, pos5, pos6);
        WarpTickEvent event = makeEvent(world, 1000, allPositions);

        new GrowCropsWarpRule().onWarpTick(true, event);

        int totalGrowth = 0;
        for (BlockPos pos : allPositions) {
            totalGrowth += world.getPropertyValue(pos, "age");
        }
        Assertions.assertTrue(totalGrowth >= 4,
                "Expected at least 4 guaranteed growths, got " + totalGrowth);
        Assertions.assertTrue(totalGrowth <= 5,
                "Expected at most 5 total growths (4 guaranteed + 1 probabilistic), got " + totalGrowth);
    }

    // ========== Edge cases ==========

    @Test
    void shouldDoNothing_whenNoWorkBlocks() {
        TestWorldAccess world = new TestWorldAccess();

        WarpTickEvent event = makeEvent(world, 4096, Collections.emptyList());

        new GrowCropsWarpRule().onWarpTick(true, event);
    }

    @Test
    void shouldSkipNonGrowableBlocks() {
        TestWorldAccess world = new TestWorldAccess()
                .withBlockProperty(POS_1, "age", 0, 7);
        // POS_2 has no age property — not growable

        WarpTickEvent event = makeEvent(world, 4096, List.of(POS_1, POS_2));

        new GrowCropsWarpRule().onWarpTick(true, event);

        Assertions.assertEquals(3, world.getPropertyValue(POS_1, "age"));
        Assertions.assertEquals(-1, world.getPropertyValue(POS_2, "age"));
    }

    @Test
    void shouldReturnTownUnchanged() {
        TestWorldAccess world = new TestWorldAccess()
                .withBlockProperty(POS_1, "age", 0, 7);

        WarpTickEvent event = makeEvent(world, 4096, List.of(POS_1));

        Boolean result = new GrowCropsWarpRule().onWarpTick(true, event);

        Assertions.assertEquals(true, result);
    }
}
