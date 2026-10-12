package ca.bradj.questown.items;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;

/**
 * campfire-sleep-feels-close-enough.md: the temp bed must be placed on the SAFE side of the
 * campfire that is CLOSEST to the sleeping player. Vanilla rejects the bed with
 * TOO_FAR_AWAY when it is more than 3 blocks from the player (ServerPlayer's
 * isReachableBedBlock), so a bed placed on the far side of the fire makes every sleep
 * attempt fail from an adjacent position.
 *
 * The mocked level is open air everywhere (isFaceSturdy true on the block below, no
 * obstruction, no fluid), so all four candidate sides are safe and the choice is purely the
 * distance scoring - the behaviour under test.
 */
class CampfireSleepSideTest {

    @BeforeAll
    static void init() {
        net.minecraft.SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();
    }

    /** Open-air world: every candidate bed spot passes isSafeToLieOn. */
    private static ServerLevel openAirLevel() {
        ServerLevel level = Mockito.mock(ServerLevel.class);
        // A mocked AIR state: no real block-state internals to NPE on, and the face-sturdiness
        // / fluid probes the handler makes are stubbed open-air-true.
        BlockState air = Mockito.mock(BlockState.class);
        Mockito.when(air.isFaceSturdy(Mockito.any(net.minecraft.world.level.Level.class),
                any(BlockPos.class), Mockito.any(Direction.class)))
                .thenReturn(true);
        Mockito.when(air.getFluidState())
                .thenReturn(net.minecraft.world.level.material.Fluids.EMPTY.defaultFluidState());
        Mockito.when(level.getBlockState(any(BlockPos.class))).thenReturn(air);
        Mockito.when(level.isUnobstructed(any(BlockState.class), any(BlockPos.class),
                any(CollisionContext.class)))
                .thenReturn(true);
        return level;
    }

    @Test
    void bedGoesOnThePlayersSideOfTheCampfire() {
        BlockPos campfire = new BlockPos(0, 70, 0);
        // Player standing 1 block north of the fire (looking at it).
        Vec3 playerPos = Vec3.atCenterOf(campfire).add(0, 0, -1);

        BlockPos head = CampfireSleepHandler.findSafeSleepPosition(openAirLevel(), campfire,
                playerPos);

        // The bed must be on the north side of the fire (the player's side), NOT the first
        // direction in iteration order: the head lands 2 blocks north of the campfire.
        assertEquals(campfire.relative(Direction.NORTH, 2), head,
                "bed head should be placed 2 blocks on the player's side of the campfire");
    }

    @Test
    void oppositeSidePlayerGetsOppositeSideBed() {
        BlockPos campfire = new BlockPos(0, 70, 0);
        Vec3 playerPos = Vec3.atCenterOf(campfire).add(0, 0, 3); // 3 blocks south

        BlockPos head = CampfireSleepHandler.findSafeSleepPosition(openAirLevel(), campfire,
                playerPos);

        assertEquals(campfire.relative(Direction.SOUTH, 2), head,
                "a player south of the fire should get a south-side bed");
    }

    @Test
    void noPlayerOverloadStillReturnsTheFirstSafeSide() {
        BlockPos campfire = new BlockPos(0, 70, 0);

        BlockPos head = CampfireSleepHandler.findSafeSleepPosition(openAirLevel(), campfire);

        // Pre-player-aware behavior: first safe direction in Direction.Plane.HORIZONTAL
        // iteration order (NORTH).
        assertEquals(campfire.relative(Direction.NORTH, 2), head);
    }

    @Test
    void openAirWorldAlwaysYieldsAValidHead() {
        BlockPos campfire = new BlockPos(0, 70, 0);
        assertNotNull(CampfireSleepHandler.findSafeSleepPosition(openAirLevel(), campfire,
                Vec3.atCenterOf(campfire)), "an open-air world always has a safe bed spot");
    }
}
