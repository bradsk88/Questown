package ca.bradj.questown.integration.minecraft;

import ca.bradj.questown.town.TownState;
import ca.bradj.questown.jobs.JobID;
import ca.bradj.questown.jobs.SimpleSnapshot;
import ca.bradj.questown.jobs.production.ProductionStatus;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The LOOT extractions a warp records: kept in order, carried through state copies, and cleared exactly once. */
class MCTownStateLootExtractTest {

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void recordLootExtract_appendsInOrder() {
        MCTownState state = singleVillagerTown();

        MCHeldItem mutton = MCHeldItem.fromTown(new MCTownItem(Items.COOKED_BEEF, 1, new net.minecraft.nbt.CompoundTag()));
        MCHeldItem pork = MCHeldItem.fromTown(new MCTownItem(Items.COOKED_PORKCHOP, 2, new net.minecraft.nbt.CompoundTag()));

        state.recordLootExtract(mutton);
        state.recordLootExtract(pork);

        List<MCHeldItem> recorded = state.recordedLootExtractions();
        assertEquals(2, recorded.size());
        assertEquals(mutton, recorded.get(0));
        assertEquals(pork, recorded.get(1));
    }

    @Test
    void recordsSurvive_stateCopy() {
        MCTownState state = singleVillagerTown();
        MCHeldItem mutton = MCHeldItem.fromTown(new MCTownItem(Items.COOKED_BEEF, 1, new net.minecraft.nbt.CompoundTag()));
        state.recordLootExtract(mutton);

        // A state copy keeps the record.
        MCTownState copied = state.setJobBlockState(new BlockPos(0, 64, 0), ca.bradj.questown.town.workstatus.State.fresh());

        List<MCHeldItem> recorded = copied.recordedLootExtractions();
        assertEquals(1, recorded.size());
        assertEquals(mutton, recorded.get(0));
    }

    @Test
    void clearRecordedLootExtractions_consumesExactlyOnce() {
        MCTownState state = singleVillagerTown();
        MCHeldItem mutton = MCHeldItem.fromTown(new MCTownItem(Items.COOKED_BEEF, 1, new net.minecraft.nbt.CompoundTag()));
        state.recordLootExtract(mutton);

        MCTownState cleared = state.clearRecordedLootExtractions();

        assertTrue(cleared.recordedLootExtractions().isEmpty(), "cleared state has no records");
        // Clearing twice is harmless.
        state.clearRecordedLootExtractions();
        assertTrue(state.recordedLootExtractions().isEmpty());
    }

    private static MCTownState singleVillagerTown() {
        TownState.VillagerData<MCHeldItem> v = new TownState.VillagerData<>(
                0, 0, 0,
                new SimpleSnapshot<ProductionStatus, MCHeldItem>(
                        new JobID("hunter", "sword"), ProductionStatus.FACTORY.idle(), ImmutableList.of()),
                UUID.randomUUID()
        );
        return new MCTownState(
                List.of(v),
                List.of(),
                ImmutableMap.of(),
                ImmutableMap.of(),
                List.of(),
                ImmutableList.of(),
                ImmutableMap.of(),
                0L
        );
    }
}
