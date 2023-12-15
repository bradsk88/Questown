package ca.bradj.questown.jobs.declarative;

import ca.bradj.questown.QT;
import ca.bradj.questown.jobs.HeldItem;
import ca.bradj.questown.jobs.WorkSpot;
import ca.bradj.questown.town.WorkStatusStore;
import com.google.common.collect.ImmutableMap;

import java.util.Map;
import java.util.function.Function;

public abstract class AbstractItemWI<POS, EXTRA, ITEM extends HeldItem<ITEM, ?>> implements ItemWI<POS, EXTRA>, WorkStatusStore.InsertionRules<ITEM> {
    private final ImmutableMap<Integer, Function<ITEM, Boolean>> ingredientsRequiredAtStates;
    private final ImmutableMap<Integer, Integer> ingredientQtyRequiredAtStates;
    private final ImmutableMap<Integer, Integer> workRequiredAtStates;
    private final ImmutableMap<Integer, Integer> timeRequiredAtStates;
    private final InventoryHandle<? extends ITEM> inventory;

    public AbstractItemWI(
            ImmutableMap<Integer, Function<ITEM, Boolean>> ingredientsRequiredAtStates,
            ImmutableMap<Integer, Integer> ingredientQtyRequiredAtStates,
            ImmutableMap<Integer, Integer> workRequiredAtStates,
            ImmutableMap<Integer, Integer> timeRequiredAtStates,
            InventoryHandle<? extends ITEM> inventory
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
        // TODO[ASAP]: Move this logic into the Abstract (tested) class
        POS bp = ws.position;
        Integer state = getState(extra, ws);
        if (state == null || !state.equals(ws.action)) {
            return false;
        }

        for (ITEM item : inventory.getItems()) {
            if (item.isEmpty()) {
                continue;
            }
            String invBefore = inventory.toString();
            String name = item.getShortName();
            if (!canInsertItem(extra, item, bp)) {
                continue;
            }
            Integer nextStepWork = workRequiredAtStates.getOrDefault(
                    ws.action + 1, 0
            );
            if (nextStepWork == null) {
                nextStepWork = 0;
            }
            Integer nextStepTime = timeRequiredAtStates.getOrDefault(
                    ws.action + 1, 0
            );
            if (nextStepTime == null) {
                nextStepTime = 0;
            }
            if (tryInsertItem(extra, this, item, bp, nextStepWork, nextStepTime)) {
                QT.JOB_LOGGER.debug("Villager removed {} from their inventory {}", name, invBefore);
                inventory.setChanged();
                return true;
            }
        }
        return false;
    }

    protected abstract boolean canInsertItem(
            EXTRA extra,
            ITEM item,
            POS bp
    );

    protected abstract boolean tryInsertItem(
            EXTRA extra,
            WorkStatusStore.InsertionRules<ITEM> rules,
            ITEM item,
            POS bp,
            Integer nextStepWork,
            Integer nextStepTime
    );

    protected abstract Integer getState(
            EXTRA extra,
            WorkSpot<Integer,POS> ws
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
