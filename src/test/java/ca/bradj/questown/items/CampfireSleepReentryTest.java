package ca.bradj.questown.items;

import ca.bradj.questown.town.entity.TownFlagBlockEntity;
import com.mojang.authlib.GameProfile;
import com.mojang.datafixers.util.Either;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.valueproviders.UniformInt;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.dimension.BuiltinDimensionTypes;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.OptionalLong;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;

/**
 * A second wand use on the campfire while the player already sleeps there must do nothing.
 * 2026-10-22 agent playtest: the second use placed another temp bed and failed to start sleep
 * (OTHER_PROBLEM); its failure path cleared the sleeper's records, so the first temp bed stayed in
 * the world, the campfire stayed lit, and the wake never granted {@code first_campfire_sleep}
 * (the "Your camp is ready" toast then came back every evening).
 */
class CampfireSleepReentryTest {

    @BeforeAll
    static void init() {
        net.minecraft.SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();
    }

    @Test
    void useWhileAsleep_placesNoBed() {
        BlockPos campfire = new BlockPos(0, 70, 0);
        ServerLevel level = litCampfireInOpenAir(campfire);
        ServerPlayer player = Mockito.mock(ServerPlayer.class);
        Mockito.when(player.isSleeping()).thenReturn(true);
        Mockito.when(player.getUUID()).thenReturn(UUID.randomUUID());
        Mockito.when(player.getGameProfile()).thenReturn(new GameProfile(UUID.randomUUID(), "Dev"));
        Mockito.when(player.position()).thenReturn(Vec3.atCenterOf(campfire).add(0, 0, -1));
        Mockito.when(player.startSleepInBed(any(BlockPos.class)))
               .thenReturn(Either.left(Player.BedSleepingProblem.OTHER_PROBLEM));

        TownFlagBlockEntity flag = Mockito.mock(TownFlagBlockEntity.class);
        Mockito.when(flag.getBlockPos()).thenReturn(campfire.north(3));

        CampfireSleepHandler.beginCampfireSleep(player, level, campfire, flag);

        Mockito.verify(level, Mockito.never()).setBlock(any(BlockPos.class), any(BlockState.class), anyInt());
        Mockito.verify(player, Mockito.never()).startSleepInBed(any(BlockPos.class));
    }

    /** DimensionType is a final record; the handler only logs {@code hasFixedTime()}. */
    private static DimensionType overworld() {
        return new DimensionType(
                OptionalLong.empty(), true, false, false, true, 1.0, true, false, -64, 384, 384,
                BlockTags.INFINIBURN_OVERWORLD, BuiltinDimensionTypes.OVERWORLD_EFFECTS, 0.0f,
                new DimensionType.MonsterSettings(false, true, UniformInt.of(0, 7), 0)
        );
    }

    /** Open air everywhere except a lit campfire at {@code campfire} (as CampfireSleepSideTest). */
    private static ServerLevel litCampfireInOpenAir(BlockPos campfire) {
        ServerLevel level = Mockito.mock(ServerLevel.class);
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
        BlockState lit = Mockito.mock(BlockState.class);
        Mockito.when(lit.hasProperty(CampfireBlock.LIT)).thenReturn(true);
        Mockito.when(lit.getValue(CampfireBlock.LIT)).thenReturn(true);
        Mockito.when(level.getBlockState(campfire)).thenReturn(lit);
        Mockito.when(level.dimensionType()).thenReturn(overworld());
        return level;
    }
}
