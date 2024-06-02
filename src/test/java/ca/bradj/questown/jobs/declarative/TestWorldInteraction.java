package ca.bradj.questown.jobs.declarative;

import ca.bradj.questown.jobs.GathererJournalTest;
import ca.bradj.questown.jobs.JobDefinition;
import ca.bradj.questown.jobs.JobID;
import ca.bradj.questown.town.AbstractWorkStatusStore;
import ca.bradj.questown.town.Claim;
import ca.bradj.questown.town.interfaces.ImmutableWorkStateContainer;
import ca.bradj.roomrecipes.core.space.Position;
import com.google.common.collect.ImmutableMap;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;

public class TestWorldInteraction extends
        AbstractWorldInteraction<Void, Position, GathererJournalTest.TestItem, GathererJournalTest.TestItem, Boolean> {

    private final InventoryHandle<GathererJournalTest.TestItem> inventory;
    boolean extracted;
    private final ImmutableWorkStateContainer<Position, Boolean> workStatuses;

    public TestWorldInteraction(
            int maxState,
            ImmutableMap<Integer, Function<GathererJournalTest.TestItem, Boolean>> toolsRequiredAtStates,
            ImmutableMap<Integer, Integer> workRequiredAtStates,
            ImmutableMap<Integer, Function<GathererJournalTest.TestItem, Boolean>> ingredientsRequiredAtStates,
            ImmutableMap<Integer, Integer> ingredientQuantityRequiredAtStates,
            ImmutableMap<Integer, Integer> timeRequiredAtStates,
            InventoryHandle<GathererJournalTest.TestItem> inventory,
            ImmutableWorkStateContainer<Position, Boolean> workStatuses,
            Supplier<Claim> claim
    ) {
        super(
                new JobID("test", "test"),
                -1, // Not used
                0,
                maxState,
                toolsRequiredAtStates,
                workRequiredAtStates,
                ingredientsRequiredAtStates,
                ingredientQuantityRequiredAtStates,
                timeRequiredAtStates,
                (v) -> claim.get()
        );
        this.workStatuses = workStatuses;
        this.inventory = inventory;
    }

    public static TestWorldInteraction forDefinition(
            JobDefinition d,
            InventoryHandle<GathererJournalTest.TestItem> inv,
            ImmutableWorkStateContainer<Position, Boolean> workStatuses,
            Supplier<Claim> claims
    ) {
        return new TestWorldInteraction(
                d.maxState(),
                itemPred(d.toolsRequiredAtStates()),
                d.workRequiredAtStates(),
                itemPred(d.ingredientsRequiredAtStates()),
                d.ingredientQtyRequiredAtStates(),
                d.timeRequiredAtStates(),
                inv, workStatuses, claims
        );
    }

    private static ImmutableMap<Integer, Function<GathererJournalTest.TestItem, Boolean>> itemPred(
            ImmutableMap<Integer, String> items
    ) {
        ImmutableMap.Builder<Integer, Function<GathererJournalTest.TestItem, Boolean>> b = ImmutableMap.builder();
        items.forEach((k, v) -> b.put(k, item -> v.equals(item.value)));
        return b.build();
    }

    @Override
    protected Boolean tryExtractProduct(
            Void unused,
            Position position
    ) {
        extracted = true;
        getWorkStatuses(null).clearState(position);
        return true;
    }

    @Override
    protected Boolean setJobBlockState(
            @NotNull Void inputs,
            Boolean ts,
            Position position,
            AbstractWorkStatusStore.State fresh
    ) {
        return null;
    }

    @Override
    protected Boolean withEffectApplied(
            @NotNull Void inputs,
            Boolean ts,
            GathererJournalTest.TestItem newItem
    ) {
        return null;
    }

    @Override
    protected Boolean withKnowledge(
            @NotNull Void inputs,
            Boolean ts,
            GathererJournalTest.TestItem newItem
    ) {
        return null;
    }

    @Override
    protected boolean isInstanze(
            GathererJournalTest.TestItem testItem,
            Class<?> clazz
    ) {
        return false;
    }

    @Override
    protected boolean isMulti(GathererJournalTest.TestItem testItem) {
        return false;
    }

    @Override
    protected Boolean getTown(Void inputs) {
        return null;
    }

    @Override
    protected Iterable<GathererJournalTest.TestItem> getResults(
            Void inputs,
            Collection<GathererJournalTest.TestItem> testItems
    ) {
        return null;
    }

    @Override
    protected boolean isEntityClose(
            Void unused,
            Position position
    ) {
        return true;
    }

    @Override
    protected boolean isReady(Void unused) {
        return true;
    }

    @Override
    public Map<Integer, Integer> ingredientQuantityRequiredAtStates() {
        return null;
    }

    @Override
    protected int getWorkSpeedOf10(Void unused) {
        return 10;
    }

    @Override
    protected int getAffectedTime(
            Void unused,
            Integer nextStepTime
    ) {
        return nextStepTime;
    }

    @Override
    protected Boolean setHeldItem(
            Void uxtra,
            Boolean tuwn,
            int villagerIndex,
            int itemIndex,
            GathererJournalTest.TestItem item
    ) {
        inventory.set(itemIndex, item);
        return true;
    }

    @Override
    protected Boolean degradeTool(
            Void unused,
            Boolean tuwn,
            Function<GathererJournalTest.TestItem, Boolean> heldItemBooleanFunction
    ) {
        return tuwn;
    }

    @Override
    protected boolean canInsertItem(
            Void unused,
            GathererJournalTest.TestItem item,
            Position bp
    ) {
        return true;
    }

    @Override
    protected ImmutableWorkStateContainer<Position, Boolean> getWorkStatuses(Void unused) {
        return workStatuses;
    }

    @Override
    protected Collection<GathererJournalTest.TestItem> getHeldItems(
            Void unused,
            int villagerIndex
    ) {
        return inventory.getItems();
    }
}
