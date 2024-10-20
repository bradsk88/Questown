package ca.bradj.questown.jobs.declarative;

import ca.bradj.questown.QT;
import ca.bradj.questown.jobs.HeldItem;
import ca.bradj.questown.jobs.WorkSpot;
import ca.bradj.questown.town.AbstractWorkStatusStore;
import ca.bradj.questown.town.Claim;
import ca.bradj.questown.town.interfaces.ImmutableWorkStateContainer;
import com.google.common.collect.ImmutableMap;
import org.apache.logging.log4j.util.TriConsumer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Function;

public abstract class AbstractItemWI<
        POS, EXTRA, ITEM extends HeldItem<ITEM, ?>, TOWN
        > implements ItemWI<POS, EXTRA, TOWN>, AbstractWorkStatusStore.InsertionRules<ITEM> {
    private final ImmutableMap<Integer, Function<ITEM, Boolean>> ingredientsRequiredAtStates;
    private final ImmutableMap<Integer, Integer> ingredientQtyRequiredAtStates;
    private final ImmutableMap<Integer, Integer> workRequiredAtStates;
    private final BiFunction<EXTRA, Integer, @NotNull Integer> timeRequiredAtStates;
    private final int villagerIndex;
    private final Function<EXTRA, Claim> claimSpots;
    private final List<TriConsumer<EXTRA, POS, ITEM>> itemInsertedListener = new ArrayList<>();

    public AbstractItemWI(
            int villagerIndex,
            ImmutableMap<Integer, Function<ITEM, Boolean>> ingredientsRequiredAtStates,
            ImmutableMap<Integer, Integer> ingredientQtyRequiredAtStates,
            ImmutableMap<Integer, Integer> workRequiredAtStates,
            BiFunction<EXTRA, Integer, @NotNull Integer> timeRequiredAtStates,
            Function<EXTRA, Claim> claimSpots
    ) {
        this.villagerIndex = villagerIndex;
        this.ingredientsRequiredAtStates = ingredientsRequiredAtStates;
        this.ingredientQtyRequiredAtStates = ingredientQtyRequiredAtStates;
        this.workRequiredAtStates = workRequiredAtStates;
        this.timeRequiredAtStates = timeRequiredAtStates;
        this.claimSpots = claimSpots;
    }

    @Override
    public TOWN tryInsertIngredients(
            EXTRA extra,
            WorkSpot<Integer, POS> ws
    ) {
        POS bp = ws.position();
        int curState = ws.action();
        AbstractWorkStatusStore.State state = getWorkStatuses(extra).getJobBlockState(ws.position());
        if (state == null || state.isFresh()) {
            Integer initWork = workRequiredAtStates.get(curState);
            if (initWork == null) {
                initWork = 0;
            }
            state = AbstractWorkStatusStore.State.fresh()
                                                 .setWorkLeft(initWork);
        }

        if (state.processingState() != curState) {
            return null;
        }

        Integer qty = ingredientQtyRequiredAtStates.get(state.processingState());
        if (qty != null && qty == state.ingredientCount()) {
            return null;
        }

        int i = -1;
        Collection<ITEM> heldItems = getHeldItems(extra, villagerIndex);
        String invBefore = String.format(
                "[%s]", String.join(", ", heldItems.stream()
                                                   .map(v -> v.toShortString())
                                                   .toList()));
        for (ITEM item : heldItems) {
            i++;
            if (item.isEmpty()) {
                continue;
            }
            String name = item.getShortName();
            if (!canInsertItem(extra, item, bp)) {
                continue;
            }
            Integer nextStepWork = workRequiredAtStates.getOrDefault(
                    curState + 1, 0
            );
            if (nextStepWork == null) {
                nextStepWork = 0;
            }
            Integer nextStepTime = timeRequiredAtStates.apply(extra, curState + 1);
            final int ii = i;
            TOWN town = tryInsertItem(extra, this, state, item, bp, nextStepWork, nextStepTime,
                    (uxtra, tuwn) -> setHeldItem(uxtra, tuwn, villagerIndex, ii, item.shrink())
            );
            if (town != null) {
                QT.JOB_LOGGER.debug("Villager removed {} from their inventory {}", name, invBefore);

                itemInsertedListener.forEach(v -> v.accept(extra, bp, item));

                Claim claim = claimSpots.apply(extra);
                if (claim != null) {
                    if (getWorkStatuses(extra).claimSpot(bp, claim)) {
                        return town;
                    }
                    return null;
                } else {
                    return town;
                }
            }
        }
        return null;
    }

    protected abstract TOWN setHeldItem(
            EXTRA uxtra,
            TOWN tuwn,
            int villagerIndex,
            int itemIndex,
            ITEM item
    );

    protected abstract Collection<ITEM> getHeldItems(
            EXTRA extra,
            int villagerIndex
    );

    private @Nullable TOWN tryInsertItem(
            EXTRA extra,
            AbstractWorkStatusStore.InsertionRules<ITEM> rules,
            AbstractWorkStatusStore.State oldState,
            ITEM item,
            POS bp,
            Integer workInNextStep,
            Integer timeInNextStep,
            BiFunction<EXTRA, TOWN, TOWN> shrinkItem
    ) {
        ImmutableWorkStateContainer<POS, TOWN> ws = getWorkStatuses(extra);
        int curValue = oldState.processingState();
        boolean canDo = false;
        Function<ITEM, Boolean> ingredient = rules.ingredientsRequiredAtStates()
                                                  .get(curValue);
        if (ingredient != null) {
            canDo = ingredient.apply(item);
        }
        Integer qtyRequired = rules.ingredientQuantityRequiredAtStates()
                                   .getOrDefault(curValue, 0);
        if (qtyRequired == null) {
            qtyRequired = 0;
        }
        int curCount = oldState.ingredientCount();
        if (canDo && curCount > qtyRequired) {
            QT.BLOCK_LOGGER.error(
                    "Somehow exceeded required quantity: can accept up to {}, had {}",
                    qtyRequired,
                    curCount
            );
        }

        int count = curCount + 1;
        boolean shrink = canDo && count <= qtyRequired;

        TOWN updatedTown = maybeUpdateBlockState(
                oldState, bp, workInNextStep, timeInNextStep, canDo, count, qtyRequired, ws);

        if (shrink) {
            return shrinkItem.apply(extra, updatedTown);
        }
        return updatedTown;
    }

    @Nullable
    private static <POS, TOWN> TOWN maybeUpdateBlockState(
            AbstractWorkStatusStore.State oldState,
            POS bp,
            Integer workInNextStep,
            Integer timeInNextStep,
            boolean canDo,
            int count,
            Integer qtyRequired,
            ImmutableWorkStateContainer<POS, TOWN> ws
    ) {
        if (canDo && count == qtyRequired && oldState.workLeft() > 0) {
            AbstractWorkStatusStore.State blockState = oldState.setCount(count);
            return ws.setJobBlockState(bp, blockState);
        }

        if (canDo && count <= qtyRequired) {
            AbstractWorkStatusStore.State blockState = oldState.setCount(count);
            if (count == qtyRequired) {
                blockState = blockState.setWorkLeft(workInNextStep)
                                       .setCount(0)
                                       .setProcessing(oldState.processingState() + 1);
            }
            if (count == qtyRequired && timeInNextStep > 0) {
                return ws.setJobBlockStateWithTimer(bp, blockState, timeInNextStep);
            } else {
                return ws.setJobBlockState(bp, blockState);
            }
        }
        return null;
    }


    protected abstract ImmutableWorkStateContainer<POS, TOWN> getWorkStatuses(EXTRA extra);

    protected abstract boolean canInsertItem(
            EXTRA extra,
            ITEM item,
            POS bp
    );

    @Override
    public Map<Integer, Function<ITEM, Boolean>> ingredientsRequiredAtStates() {
        return ingredientsRequiredAtStates;
    }

    @Override
    public Map<Integer, Integer> ingredientQuantityRequiredAtStates() {
        return ingredientQtyRequiredAtStates;
    }

    public void addItemInsertionListener(TriConsumer<EXTRA, POS, ITEM> listener) {
        this.itemInsertedListener.add(listener);
    }

    public void removeItemInsertionListener(TriConsumer<EXTRA, POS, ITEM> listener) {
        this.itemInsertedListener.remove(listener);
    }
}
