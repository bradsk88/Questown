package ca.bradj.questown.town.entity;

import ca.bradj.questown.town.TownWorkStatusStore;
import ca.bradj.questown.town.workstatus.State;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

class MiningSoundsTest {

    private static final BlockPos SHAFT = new BlockPos(1, 64, 1);
    private static final BlockPos FURNACE = new BlockPos(5, 64, 5);

    /** Dev playtest 2026-10-30: pickaxe sounds while a villager is mining. */
    @Test
    void aShaftWithATripTimer_isBeingMined() {
        TownWorkStatusStore miner = new TownWorkStatusStore((m, p) -> {});
        miner.setJobBlockStateWithTimer(SHAFT, State.freshAtState(1), 1200);
        Assertions.assertEquals(Set.of(SHAFT), Set.copyOf(MiningSounds.minedShafts(List.of(miner), SHAFT::equals)));
    }

    @Test
    void aShaftWithNoTimer_orATimedFurnace_isQuiet() {
        TownWorkStatusStore idle = new TownWorkStatusStore((m, p) -> {});
        idle.setJobBlockState(SHAFT, State.freshAtState(0));
        TownWorkStatusStore cook = new TownWorkStatusStore((m, p) -> {});
        cook.setJobBlockStateWithTimer(FURNACE, State.freshAtState(1), 200);
        Assertions.assertTrue(MiningSounds.minedShafts(List.of(idle, cook), SHAFT::equals).isEmpty());
    }
}
