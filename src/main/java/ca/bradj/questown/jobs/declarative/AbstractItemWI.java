package ca.bradj.questown.jobs.declarative;

import ca.bradj.questown.QT;
import ca.bradj.questown.jobs.HeldItem;
import ca.bradj.questown.jobs.WorkedSpot;
import ca.bradj.questown.logic.PredicateCollection;
import ca.bradj.questown.town.AbstractWorkStatusStore;
import ca.bradj.questown.town.Claim;
import ca.bradj.questown.town.interfaces.ImmutableWorkStateContainer;
import ca.bradj.questown.town.workstatus.State;
import org.apache.logging.log4j.util.TriConsumer;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Function;

public abstract class AbstractItemWI<
        POS, EXTRA, ITEM extends HeldItem<ITEM, ?>, TOWN
        > implements ItemWI<POS, EXTRA, TOWN, ITEM>, AbstractWorkStatusStore.InsertionRules<ITEM> {
    private final int villagerIndex;
    private final Function<EXTRA, Claim> claimSpots;
    private final List<TriConsumer<EXTRA, POS, ITEM>> itemInsertedListener = new ArrayList<>();
    private final ItemWorkChecks<EXTRA, ITEM, ?> checks;

    public <S> AbstractItemWI(
            int villagerIndex,
            ItemWorkChecks<EXTRA, ITEM, ?> checks,
            Function<EXTRA, Claim> claimSpots
    ) {
        this.villagerIndex = villagerIndex;
        this.checks = checks;
        this.claimSpots = claimSpots;
    }

    @Override
    public InsertResult<TOWN, ITEM> tryInsertIngredients(
            EXTRA extra,
            WorkedSpot<POS> ws
    ) {
        POS bp = ws.workPosition();
        int curState = ws.stateAfterWork();
        State state = getWorkStatuses(extra).getJobBlockState(bp);
        if (state == null || state.isFresh()) {
            Integer initWork = checks.getWorkForStep(curState, 0);
            //noinspection DataFlowIssue
            state = State.fresh().setWorkLeft(initWork);
        }

        if (state.processingState() != curState) {
            return null;
        }

        Integer qty = checks.getQuantityForStep(state.processingState(), null);
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
            int nextStepWork = checks.getWorkForStep(curState + 1, 0);
            int nextStepTime = checks.getTimeForStep(extra, curState + 1, 0);
            final int ii = i;
            TOWN town = tryInsertItem(extra, this, state, item, bp, nextStepWork, nextStepTime,
                    (uxtra, tuwn) -> setHeldItem(uxtra, tuwn, villagerIndex, ii, item.shrink())
            );
            if (town != null) {
                InsertResult<TOWN, ITEM> res = new InsertResult<>(town, item);
                QT.JOB_LOGGER.debug("Villager removed {} from their inventory {}", name, invBefore);

                itemInsertedListener.forEach(v -> v.accept(extra, bp, item));

                Claim claim = claimSpots.apply(extra);
                if (claim != null) {
                    if (getWorkStatuses(extra).claimSpot(bp, claim)) {
                        return res;
                    }
                    return null;
                } else {
                    return res;
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
            State oldState,
            ITEM item,
            POS bp,
            int workInNextStep,
            int timeInNextStep,
            BiFunction<EXTRA, TOWN, TOWN> shrinkItem
    ) {
        ImmutableWorkStateContainer<POS, TOWN> ws = getWorkStatuses(extra);
        int curValue = oldState.processingState();
        boolean canDo = false;
        PredicateCollection<ITEM, ?> ingredient = rules.getIngredientsRequiredAtState(curValue);
        if (ingredient != null) {
            canDo = ingredient.test(item);
        }
        //noinspection DataFlowIssue
        int qtyRequired = rules.getIngredientQuantityRequiredAtState(curValue, 0);
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
            State oldState,
            POS bp,
            int workInNextStep,
            int timeInNextStep,
            boolean canDo,
            int count,
            int qtyRequired,
            ImmutableWorkStateContainer<POS, TOWN> ws
    ) {
        if (canDo && count == qtyRequired && oldState.workLeft() > 0) {
            State blockState = oldState.setCount(count);
            return ws.setJobBlockState(bp, blockState);
        }

        if (canDo && count <= qtyRequired) {
            State blockState = oldState.setCount(count);
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
    public @Nullable Integer getIngredientQuantityRequiredAtState(int state, @Nullable Integer orDefault) {
        return checks.getQuantityForStep(state, orDefault);
    }

    @Override
    public @Nullable PredicateCollection<ITEM, ?> getIngredientsRequiredAtState(Integer state) {
        return checks.getIngredientsForStep(state);
    }

    public void addItemInsertionListener(TriConsumer<EXTRA, POS, ITEM> listener) {
        this.itemInsertedListener.add(listener);
    }

    public void removeItemInsertionListener(TriConsumer<EXTRA, POS, ITEM> listener) {
        this.itemInsertedListener.remove(listener);
    }
}
