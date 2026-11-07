package ca.bradj.questown.items;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;

/** The temp bed goes on the campfire's side nearest the player; vanilla won't let a player sleep in a bed more than 3 blocks away. */
class CampfireSleepSideTest {

    @BeforeAll
    static void init() {
        net.minecraft.SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();
    }

    private static ServerLevel openAirLevel() {
        ServerLevel level = Mockito.mock(ServerLevel.class);
        // A mocked air state, so every side is safe and only the distance decides.
        BlockState air = Mockito.mock(BlockState.class);
        Mockito.when(air.isFaceSturdy(Mockito.any(net.minecraft.world.level.Level.class),
                any(BlockPos.class), Mockito.any(Direction.class)))
                .thenReturn(true);
        Mockito.when(air.getMaterial()).thenReturn(net.minecraft.world.level.material.Material.AIR);
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
        Vec3 playerPos = Vec3.atCenterOf(campfire).add(0, 0, -1);

        BlockPos head = CampfireSleepHandler.findSafeSleepPosition(openAirLevel(), campfire,
                playerPos);

        // On the player's side, two blocks north of the fire.
        assertEquals(campfire.relative(Direction.NORTH, 2), head,
                "bed head should be placed 2 blocks on the player's side of the campfire");
    }

    @Test
    void oppositeSidePlayerGetsOppositeSideBed() {
        BlockPos campfire = new BlockPos(0, 70, 0);
        Vec3 playerPos = Vec3.atCenterOf(campfire).add(0, 0, 3);

        BlockPos head = CampfireSleepHandler.findSafeSleepPosition(openAirLevel(), campfire,
                playerPos);

        assertEquals(campfire.relative(Direction.SOUTH, 2), head,
                "a player south of the fire should get a south-side bed");
    }

    @Test
    void noPlayerOverloadStillReturnsTheFirstSafeSide() {
        BlockPos campfire = new BlockPos(0, 70, 0);

        BlockPos head = CampfireSleepHandler.findSafeSleepPosition(openAirLevel(), campfire);

        // With no player: the first safe direction, north.
        assertEquals(campfire.relative(Direction.NORTH, 2), head);
    }

    @Test
    void openAirWorldAlwaysYieldsAValidHead() {
        BlockPos campfire = new BlockPos(0, 70, 0);
        assertNotNull(CampfireSleepHandler.findSafeSleepPosition(openAirLevel(), campfire,
                Vec3.atCenterOf(campfire)), "an open-air world always has a safe bed spot");
    }
}
