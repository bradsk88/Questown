package ca.bradj.questown.jobs.declarative;

import ca.bradj.questown.QT;
import ca.bradj.questown.jobs.GathererJournalTest;
import ca.bradj.questown.jobs.JobDefinition;
import ca.bradj.questown.jobs.JobID;
import ca.bradj.questown.jobs.WorkSpot;
import ca.bradj.questown.town.Claim;
import ca.bradj.questown.town.interfaces.ImmutableWorkStateContainer;
import ca.bradj.questown.town.workstatus.State;
import ca.bradj.roomrecipes.core.space.Position;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;

public class TestWorldInteraction extends
        AbstractWorldInteraction<Void, Position, GathererJournalTest.TestItem, GathererJournalTest.TestItem, Boolean> {

    private final ValidatedInventoryHandle<GathererJournalTest.TestItem> inventory;
    private Iterable<GathererJournalTest.TestItem> results = ImmutableList.of();
    boolean extracted;
    private final ImmutableWorkStateContainer<Position, Boolean> workStatuses;

    public TestWorldInteraction(
            int maxState,
            ImmutableMap<Integer, Function<GathererJournalTest.TestItem, Boolean>> toolsRequiredAtStates,
            ImmutableMap<Integer, Integer> workRequiredAtStates,
            ImmutableMap<Integer, Function<GathererJournalTest.TestItem, Boolean>> ingredientsRequiredAtStates,
            ImmutableMap<Integer, Integer> ingredientQuantityRequiredAtStates,
            ImmutableMap<Integer, Integer> timeRequiredAtStates,
            Iterable<GathererJournalTest.TestItem> results,
            ValidatedInventoryHandle<GathererJournalTest.TestItem> inventory,
            ImmutableWorkStateContainer<Position, Boolean> workStatuses,
            Supplier<Claim> claim
    ) {
        this(
                maxState,
                toolsRequiredAtStates,
                workRequiredAtStates,
                ingredientsRequiredAtStates,
                ingredientQuantityRequiredAtStates,
                timeRequiredAtStates,
                inventory,
                workStatuses,
                claim
        );
        this.results = results;
    }

    public TestWorldInteraction(
            int maxState,
            ImmutableMap<Integer, Function<GathererJournalTest.TestItem, Boolean>> toolsRequiredAtStates,
            ImmutableMap<Integer, Integer> workRequiredAtStates,
            ImmutableMap<Integer, Function<GathererJournalTest.TestItem, Boolean>> ingredientsRequiredAtStates,
            ImmutableMap<Integer, Integer> ingredientQuantityRequiredAtStates,
            ImmutableMap<Integer, Integer> timeRequiredAtStates,
            ValidatedInventoryHandle<GathererJournalTest.TestItem> inventory,
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
            ValidatedInventoryHandle<GathererJournalTest.TestItem> inv,
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
                ImmutableList.of(new GathererJournalTest.TestItem(d.result())),
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
        return super.tryExtractProduct(unused, position);
    }

    @Override
    protected Boolean setJobBlockState(
            @NotNull Void inputs,
            Boolean ts,
            Position position,
            State fresh
    ) {
        return workStatuses.setJobBlockState(position, fresh);
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
        return results;
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
    public boolean tryGrabbingInsertedSupplies(Void mcExtra) {
        return false; // Arbitrary
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
    protected ArrayList<WorkSpot<Integer, Position>> shuffle(
            Void unused,
            Collection<WorkSpot<Integer, Position>> workSpots
    ) {
        QT.JOB_LOGGER.error("Shuffling has no effect in unit tests");
        return new ArrayList<>(workSpots);
    }

    @Override
    protected Collection<GathererJournalTest.TestItem> getHeldItems(
            Void unused,
            int villagerIndex
    ) {
        return inventory.getItems();
    }
}
