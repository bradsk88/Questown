package ca.bradj.questown.jobs.blacksmith;

import ca.bradj.questown.jobs.GathererJournalTest;
import ca.bradj.questown.jobs.declarative.InventoryHandle;
import ca.bradj.questown.jobs.declarative.TestWorldInteraction;
import ca.bradj.questown.town.interfaces.ImmutableWorkStateContainer;
import ca.bradj.roomrecipes.core.space.Position;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collection;

import static ca.bradj.questown.jobs.blacksmith.BlacksmithWoodenPickaxeNoMCJob.DEFINITION;

public class BlacksmithWoodenPickaxeJobTest {

    @Test
    public void testHappyPath() {
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
        TestWorldInteraction wi = TestWorldInteraction.forDefinition(
                DEFINITION, inv, ws, () -> null
        );
    }

}
