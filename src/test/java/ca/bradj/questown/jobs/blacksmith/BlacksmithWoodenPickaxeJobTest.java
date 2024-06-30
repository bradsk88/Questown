package ca.bradj.questown.jobs.blacksmith;

import ca.bradj.questown.jobs.GathererJournalTest;
import ca.bradj.questown.jobs.WorkOutput;
import ca.bradj.questown.jobs.WorkSpot;
import ca.bradj.questown.jobs.declarative.InventoryHandle;
import ca.bradj.questown.jobs.declarative.ItemCountMismatch;
import ca.bradj.questown.jobs.declarative.TestWorldInteraction;
import ca.bradj.questown.jobs.declarative.ValidatedInventoryHandle;
import ca.bradj.questown.town.interfaces.ImmutableWorkStateContainer;
import ca.bradj.questown.town.workstatus.State;
import ca.bradj.roomrecipes.core.space.Position;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collection;

import static ca.bradj.questown.jobs.blacksmith.nomc.BlacksmithWoodenPickaxeWork.*;

public class BlacksmithWoodenPickaxeJobTest {

    private static final Position ArbitraryWorkSpot = new Position(1, 2);

    @Test
    public void testExtractResult() throws ItemCountMismatch {
        // 3-slot empty inventory
        ArrayList<GathererJournalTest.TestItem> villagerInventory = new ArrayList<>();
        villagerInventory.add(new GathererJournalTest.TestItem(""));
        villagerInventory.add(new GathererJournalTest.TestItem(""));
        villagerInventory.add(new GathererJournalTest.TestItem(""));

        InventoryHandle<GathererJournalTest.TestItem> inv = new InventoryHandle<>() {
            @Override
            public Collection<GathererJournalTest.TestItem> getItems() {
                return villagerInventory;
            }

            @Override
            public void set(
                    int ii,
                    GathererJournalTest.TestItem shrink
            ) {
                villagerInventory.set(ii, shrink);
            }
        };
        ImmutableWorkStateContainer<Position, Boolean> ws = new MapBackedWSC();
        State bs = State.freshAtState(BLOCK_STATE_DONE);
        ws.setJobBlockState(ArbitraryWorkSpot, bs);

        TestWorldInteraction wi = TestWorldInteraction.forDefinition(
                DEFINITION, new ValidatedInventoryHandle<>(inv, 3), ws, () -> null
        );
        WorkSpot<Integer, Position> spot = new WorkSpot<>(
                ArbitraryWorkSpot,
                BLOCK_STATE_DONE,
                0,
                ArbitraryWorkSpot
        );
        @Nullable WorkOutput<Boolean, WorkSpot<Integer, Position>> op =
                wi.tryWorking(
                        null,
                        spot
                );

        Assertions.assertEquals(State.fresh(), ws.getJobBlockState(ArbitraryWorkSpot));
    }

}
