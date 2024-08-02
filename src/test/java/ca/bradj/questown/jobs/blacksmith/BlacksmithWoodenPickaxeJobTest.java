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
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collection;

import static ca.bradj.questown.jobs.blacksmith.nomc.BlacksmithWoodenPickaxeWork.*;

public class BlacksmithWoodenPickaxeJobTest {

    private static final Position ArbitraryWorkSpotPos = new Position(1, 2);

    @Test
    public void testExtractResult() throws ItemCountMismatch {
        // 3-slot empty inventory
        ValidatedInventoryHandle<GathererJournalTest.TestItem> inv = testInventory();
        ImmutableWorkStateContainer<Position, Boolean> ws = new MapBackedWSC();
        State bs = State.freshAtState(BLOCK_STATE_DONE);
        ws.setJobBlockState(ArbitraryWorkSpotPos, bs);

        TestWorldInteraction wi = TestWorldInteraction.forDefinition(
                DEFINITION, inv, ws, () -> null
        );
        WorkSpot<Integer, Position> spot = getArbitrarySpot(BLOCK_STATE_DONE);

        wi.tryWorking(null, spot.workPos());

        Assertions.assertEquals(State.fresh(), ws.getJobBlockState(ArbitraryWorkSpotPos));
    }

    @Test
    public void testInsertIngredientsShouldIncrementState() throws ItemCountMismatch {
        @Nullable WorkOutput<Boolean, WorkSpot<Integer, Position>> op; // Used for debugging
        ValidatedInventoryHandle<GathererJournalTest.TestItem> inv = testInventory();

        // Definition requires two sticks
        inv.set(0, new GathererJournalTest.TestItem("minecraft:stick"));
        inv.set(1, new GathererJournalTest.TestItem("minecraft:stick"));

        ImmutableWorkStateContainer<Position, Boolean> ws = new MapBackedWSC();
        TestWorldInteraction wi = TestWorldInteraction.forDefinition(
                DEFINITION, inv, ws, () -> null
        );

        State bs = State.freshAtState(BLOCK_STATE_NEED_HANDLE);
        ws.setJobBlockState(ArbitraryWorkSpotPos, bs);

        WorkSpot<Integer, Position> spot = getArbitrarySpot(BLOCK_STATE_NEED_HANDLE);
        wi.tryWorking(null, spot.workPos());

        Assertions.assertEquals(State.fresh().incrIngredientCount(), ws.getJobBlockState(ArbitraryWorkSpotPos));
        wi.tryWorking(null, spot.workPos());

        Assertions.assertEquals(State.fresh().incrProcessing(), ws.getJobBlockState(ArbitraryWorkSpotPos));
    }

    private static @NotNull WorkSpot<Integer, Position> getArbitrarySpot(int blockStateNeedHandle) {
        return new WorkSpot<>(
                ArbitraryWorkSpotPos,
                blockStateNeedHandle,
                0,
                ArbitraryWorkSpotPos
        );
    }

    private static @NotNull ValidatedInventoryHandle<GathererJournalTest.TestItem> testInventory() throws ItemCountMismatch {
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
        return new ValidatedInventoryHandle<>(inv, 3);
    }

}
