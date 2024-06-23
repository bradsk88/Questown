package ca.bradj.questown.jobs.blacksmith;

import ca.bradj.questown.jobs.GathererJournalTest;
import ca.bradj.questown.jobs.WorkOutput;
import ca.bradj.questown.jobs.WorkSpot;
import ca.bradj.questown.jobs.declarative.InventoryHandle;
import ca.bradj.questown.jobs.declarative.TestWorldInteraction;
import ca.bradj.questown.town.interfaces.ImmutableWorkStateContainer;
import ca.bradj.questown.town.workstatus.State;
import ca.bradj.roomrecipes.core.space.Position;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collection;

import static ca.bradj.questown.jobs.blacksmith.nomc.BlacksmithWoodenPickaxeWork.BLOCK_STATE_NEED_WORK;
import static ca.bradj.questown.jobs.blacksmith.nomc.BlacksmithWoodenPickaxeWork.DEFINITION;

public class BlacksmithWoodenPickaxeJobTest {

    private static final Position ArbitraryWorkSpot = new Position(1, 2);

    @Test
    public void testFinishWork() {
        ArrayList<GathererJournalTest.TestItem> villagerInventory = new ArrayList<>();
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
        State bs = State.freshAtState(BLOCK_STATE_NEED_WORK);
        ws.setJobBlockState(ArbitraryWorkSpot, bs);

        TestWorldInteraction wi = TestWorldInteraction.forDefinition(
                DEFINITION, inv, ws, () -> null
        );
        WorkSpot<Integer, Position> spot = new WorkSpot<>(
                ArbitraryWorkSpot,
                BLOCK_STATE_NEED_WORK,
                0,
                ArbitraryWorkSpot
        );
        @Nullable WorkOutput<Boolean, WorkSpot<Integer, Position>> op =
                wi.tryWorking(
                        null,
                        spot
                );

        Assertions.assertEquals(ws.getJobBlockState(ArbitraryWorkSpot), State.fresh());
    }

}
