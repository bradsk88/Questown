package ca.bradj.questown.mobs.helperchicken;

import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;

/**
 * 2026-10-30 agent playtest: the helper chicken stood in a cornflower when it got its seeds, and
 * the statue went one block up, floating. The block states are mocked (real ones NPE on terrain
 * queries outside mod-load, AGENTS.md).
 */
class ChickenStatuePosTest {

    private static final BlockPos AT = new BlockPos(56, 68, -2258);

    @BeforeAll
    static void init() {
        net.minecraft.SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();
    }

    private static BlockState state(boolean replaceable, boolean smallFlower) {
        BlockState s = Mockito.mock(BlockState.class);
        // 1.19.2: replaceability is the material's (Material is final, so use real ones).
        Mockito.when(s.getMaterial()).thenReturn(replaceable
                ? net.minecraft.world.level.material.Material.REPLACEABLE_PLANT
                : net.minecraft.world.level.material.Material.STONE);
        Mockito.when(s.is(BlockTags.SMALL_FLOWERS)).thenReturn(smallFlower);
        return s;
    }

    private static BlockGetter level(BlockState at, BlockState above) {
        BlockGetter level = Mockito.mock(BlockGetter.class);
        Mockito.when(level.getBlockState(any(BlockPos.class))).thenReturn(above);
        Mockito.when(level.getBlockState(AT)).thenReturn(at);
        return level;
    }

    @Test
    void aChickenStandingInAFlower_getsItsStatueWhereItStood() {
        BlockGetter level = level(state(false, true), state(true, false));

        assertEquals(AT, ChickenStatueTransformHandler.statuePos(level, AT));
    }

    @Test
    void aChickenInGrassOrAir_getsItsStatueWhereItStood() {
        BlockGetter level = level(state(true, false), state(true, false));

        assertEquals(AT, ChickenStatueTransformHandler.statuePos(level, AT));
    }

    @Test
    void aSolidBlockWhereItStood_pushesTheStatueUp() {
        BlockGetter level = level(state(false, false), state(true, false));

        assertEquals(AT.above(), ChickenStatueTransformHandler.statuePos(level, AT));
    }
}
