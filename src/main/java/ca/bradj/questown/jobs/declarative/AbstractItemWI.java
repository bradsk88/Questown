package ca.bradj.questown.jobs.declarative;

import ca.bradj.questown.QT;
import ca.bradj.questown.jobs.HeldItem;
import ca.bradj.questown.jobs.WorkSpot;
import ca.bradj.questown.town.WorkStatusStore;
import ca.bradj.questown.town.interfaces.WorkStateContainer;
import com.google.common.collect.ImmutableMap;

import java.util.Map;
import java.util.function.Function;

public abstract class AbstractItemWI<POS, EXTRA, ITEM extends HeldItem<ITEM, ?>> implements ItemWI<POS, EXTRA>, WorkStatusStore.InsertionRules<ITEM> {
    private final ImmutableMap<Integer, Function<ITEM, Boolean>> ingredientsRequiredAtStates;
    private final ImmutableMap<Integer, Integer> ingredientQtyRequiredAtStates;
    private final ImmutableMap<Integer, Integer> workRequiredAtStates;
    private final ImmutableMap<Integer, Integer> timeRequiredAtStates;
    private final InventoryHandle<ITEM> inventory;

    public AbstractItemWI(
            ImmutableMap<Integer, Function<ITEM, Boolean>> ingredientsRequiredAtStates,
            ImmutableMap<Integer, Integer> ingredientQtyRequiredAtStates,
            ImmutableMap<Integer, Integer> workRequiredAtStates,
            ImmutableMap<Integer, Integer> timeRequiredAtStates,
            InventoryHandle<ITEM> inventory
    ) {
        this.ingredientsRequiredAtStates = ingredientsRequiredAtStates;
        this.ingredientQtyRequiredAtStates = ingredientQtyRequiredAtStates;
        this.workRequiredAtStates = workRequiredAtStates;
        this.timeRequiredAtStates = timeRequiredAtStates;
        this.inventory = inventory;
    }

    @Override
    public boolean tryInsertIngredients(
            EXTRA extra,
            WorkSpot<Integer, POS> ws
    ) {
        POS bp = ws.position;
        WorkStatusStore.State state = getWorkStatuses(extra).getJobBlockState(ws.position);
        if (state == null) {
            state = WorkStatusStore.State.fresh();
        }
        int curState = ws.action;

        if (state.processingState() != curState) {
            return false;
        }
        Integer count = ingredientQtyRequiredAtStates.get(curState);
        if (count == null) {
            count = 0;
        }

        Integer work = workRequiredAtStates.get(curState);
        if (work == null) {
            work = 0;
        }
        state = state.setCount(count).setWorkLeft(work);
        if (state.workLeft() != 0) {
            throw new IllegalStateException();
        }

        int i = -1;
        for (ITEM item : inventory.getItems()) {
            i++;
            if (item.isEmpty()) {
                continue;
            }
            String invBefore = inventory.toString();
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
            Integer nextStepTime = timeRequiredAtStates.getOrDefault(
                    curState + 1, 0
            );
            if (nextStepTime == null) {
                nextStepTime = 0;
            }
            final int ii = i;
            if (tryInsertItem(extra, this, item, bp, nextStepWork, nextStepTime, () -> inventory.set(ii, item.shrink()))) {
                QT.JOB_LOGGER.debug("Villager removed {} from their inventory {}", name, invBefore);
                return true;
            }
        }
        return false;
    }

    private boolean tryInsertItem(
            EXTRA extra,
            WorkStatusStore.InsertionRules<ITEM> rules,
            ITEM item,
            POS bp,
            Integer workToNextStep,
            Integer timeToNextStep,
            Runnable shrinker
    ) {
        WorkStateContainer<POS> ws = getWorkStatuses(extra);
        WorkStatusStore.State oldState = ws.getJobBlockState(bp);
        if (oldState == null) {
            oldState = WorkStatusStore.State.fresh();
        }
        int curValue = oldState.processingState();
        boolean canDo = false;
        Function<ITEM, Boolean> ingredient = rules.ingredientsRequiredAtStates().get(curValue);
        if (ingredient != null) {
            canDo = ingredient.apply(item);
        }
        Integer qtyRequired = rules.ingredientQuantityRequiredAtStates().getOrDefault(curValue, 0);
        if (qtyRequired == null) {
            qtyRequired = 0;
        }
        int curCount = oldState.ingredientCount();
        if (canDo && curCount >= qtyRequired) {
            QT.BLOCK_LOGGER.error(
                    "Somehow exceeded required quantity: can accept up to {}, had {}",
                    qtyRequired,
                    curCount
            );
        }

        if (canDo && curCount < qtyRequired) {
            shrinker.run();
            int count = curCount + 1;
            WorkStatusStore.State blockState = oldState.setCount(count);
            if (timeToNextStep > 0) {
                ws.setJobBlockStateWithTimer(bp, blockState, timeToNextStep);
            } else {
                ws.setJobBlockState(bp, blockState);
            }
            if (count < qtyRequired) {
                return true;
            }

            if (oldState.workLeft() == 0) {
                int val = curValue + 1;
                blockState = blockState.setProcessing(val);
                blockState = blockState.setWorkLeft(workToNextStep);
                blockState = blockState.setCount(0);
                if (timeToNextStep > 0) {
                    ws.setJobBlockStateWithTimer(bp, blockState, timeToNextStep);
                } else {
                    ws.setJobBlockState(bp, blockState);
                }
            }
            return true;
        }
        return false;
    }

    protected abstract WorkStateContainer<POS> getWorkStatuses(EXTRA extra);

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
}
