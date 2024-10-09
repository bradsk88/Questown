package ca.bradj.questown.jobs;

import ca.bradj.questown.core.UtilClean;
import ca.bradj.questown.jobs.declarative.ItemWorkChecks;
import ca.bradj.questown.logic.PredicateCollection;
import ca.bradj.roomrecipes.adapter.IRoomRecipeMatch;
import com.google.common.collect.ImmutableMap;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.function.Predicate;

public class DeclarativeJobChecks<EXTRA, HELD_ITEM, TOWN_ITEM, ROOM extends IRoomRecipeMatch<?, ?, ?, ?>, POS> implements
        ItemWorkChecks<EXTRA, HELD_ITEM, TOWN_ITEM> {
    private ImmutableMap<Integer, PredicateCollection<HELD_ITEM, HELD_ITEM>> ingredientsRequiredAtStates;
    private ImmutableMap<Integer, Integer> ingredientsQtyRequiredAtStates;
    private ImmutableMap<Integer, PredicateCollection<TOWN_ITEM, TOWN_ITEM>> toolsRequiredAtStates;
    private ImmutableMap<Integer, Integer> workRequiredAtStates;
    private ImmutableMap<Integer, Integer> timeRequiredAtStates;
    private Predicate<ROOM> includeSupplyRoom;
    private Predicate<POS> isJobBlock;
    private boolean initialized = false;

    public DeclarativeJobChecks(
            Map<Integer, PredicateCollection<HELD_ITEM, HELD_ITEM>> ingredientsRequiredAtStates,
            Map<Integer, Integer> ingredientsQtyRequiredAtStates,
            Map<Integer, PredicateCollection<TOWN_ITEM, TOWN_ITEM>> toolsRequiredAtStates,
            Map<Integer, Integer> workRequiredAtStates,
            Map<Integer, Integer> timeRequiredAtStates,
            Predicate<ROOM> includeSupplyRoom,
            Predicate<POS> isJobBlock
    ) {
        this.ingredientsRequiredAtStates = ImmutableMap.copyOf(ingredientsRequiredAtStates);
        this.ingredientsQtyRequiredAtStates = ImmutableMap.copyOf(ingredientsQtyRequiredAtStates);
        this.toolsRequiredAtStates = ImmutableMap.copyOf(toolsRequiredAtStates);
        this.workRequiredAtStates = ImmutableMap.copyOf(workRequiredAtStates);
        this.timeRequiredAtStates = ImmutableMap.copyOf(timeRequiredAtStates);
        this.includeSupplyRoom = includeSupplyRoom;
        this.isJobBlock = isJobBlock;
    }

    public void initialize(
            Map<Integer, PredicateCollection<HELD_ITEM, HELD_ITEM>> ingr,
            Map<Integer, Integer> qty,
            Map<Integer, ? extends PredicateCollection<TOWN_ITEM, TOWN_ITEM>> tools,
            Map<Integer, Integer> work,
            Map<Integer, Integer> time,
            Predicate<ROOM> includeSupplyRoomz,
            Predicate<POS> isJobBlock
    ) {
        if (initialized) {
            throw new IllegalStateException("Already initialized");
        }
        this.ingredientsRequiredAtStates = ImmutableMap.copyOf(ingr);
        this.ingredientsQtyRequiredAtStates = ImmutableMap.copyOf(qty);
        this.toolsRequiredAtStates = ImmutableMap.copyOf(tools);
        this.workRequiredAtStates = ImmutableMap.copyOf(work);
        this.timeRequiredAtStates = ImmutableMap.copyOf(time);
        this.includeSupplyRoom = includeSupplyRoomz;
        this.isJobBlock = isJobBlock;
        this.initialized = true;
    }

    public boolean isInsufficient() {
        return toolsRequiredAtStates.isEmpty() &&
                workRequiredAtStates.isEmpty() &&
                ingredientsQtyRequiredAtStates.isEmpty() &&
                timeRequiredAtStates.isEmpty();
    }

    @Override
    public @Nullable Integer getQuantityForStep(
            int i,
            @Nullable Integer orDefault
    ) {
        return UtilClean.getOrDefault(ingredientsQtyRequiredAtStates, i, orDefault);
    }

    @Override
    public @Nullable PredicateCollection<HELD_ITEM, HELD_ITEM> getIngredientsForStep(int i) {
        return ingredientsRequiredAtStates.get(i);
    }

    @Override
    public @Nullable Integer getWorkForStep(int stepState) {
        return workRequiredAtStates.get(stepState);
    }

    @Override
    public int getWorkForStep(
            int stepState,
            int orDefault
    ) {
        return 0;
    }

    @Override
    public @Nullable Integer getTimeForStep(
            EXTRA extra,
            int stepState
    ) {
        return timeRequiredAtStates.get(stepState);
    }

    @Override
    public int getTimeForStep(
            EXTRA extra,
            int stepState,
            int orDefault
    ) {
        return UtilClean.getOrDefault(timeRequiredAtStates, stepState, orDefault);
    }

    @Override
    public @Nullable PredicateCollection<TOWN_ITEM, ?> getToolsForStep(Integer curState) {
        return toolsRequiredAtStates.get(curState);
    }

    @Override
    public boolean isWorkRequiredAtStep(int action) {
        Integer workForStep = getWorkForStep(action);
        return workForStep != null && workForStep > 0;
    }

    public Map<Integer, PredicateCollection<HELD_ITEM, HELD_ITEM>> getAllRequiredIngredients() {
        return ingredientsRequiredAtStates;
    }

    public Map<Integer, PredicateCollection<TOWN_ITEM, TOWN_ITEM>> getAllRequiredTools() {
        return toolsRequiredAtStates;
    }

    public Map<Integer, Integer> getAllRequiredWork() {
        return workRequiredAtStates;
    }

    public boolean shouldCheckContainerForSupplies(ROOM mcRoom) {
        return includeSupplyRoom.test(mcRoom);
    }

    public boolean isJobBlock(POS bp) {
        return isJobBlock.test(bp);
    }

    public Map<Integer, Integer> getAllRequiredQuantity() {
        return ingredientsQtyRequiredAtStates;
    }

    public Map<Integer, Integer> getAllRequiredTime() {
        return timeRequiredAtStates;
    }
}
