package ca.bradj.questown.jobs.declarative;

import ca.bradj.questown.QT;
import ca.bradj.questown.jobs.*;
import ca.bradj.questown.mc.Util;
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
import java.util.function.Predicate;

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
    private final Function<Integer, Collection<String>> specialRules;

    public AbstractItemWI(
            int villagerIndex,
            ImmutableMap<Integer, Function<ITEM, Boolean>> ingredientsRequiredAtStates,
            ImmutableMap<Integer, Integer> ingredientQtyRequiredAtStates,
            ImmutableMap<Integer, Integer> workRequiredAtStates,
            BiFunction<EXTRA, Integer, @NotNull Integer> timeRequiredAtStates,
            Function<EXTRA, Claim> claimSpots,
            Function<Integer, Collection<String>> specialRules
    ) {
        this.villagerIndex = villagerIndex;
        this.ingredientsRequiredAtStates = ingredientsRequiredAtStates;
        this.ingredientQtyRequiredAtStates = ingredientQtyRequiredAtStates;
        this.workRequiredAtStates = workRequiredAtStates;
        this.timeRequiredAtStates = timeRequiredAtStates;
        this.claimSpots = claimSpots;
        this.specialRules = specialRules;
    }

    @Override
    public @NotNull OrReason<TOWN> tryInsertIngredients(
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
            return OrReason.reasonOnly(String.format(
                    "State at %s has changed since last work decision [%s => %s]",
                    ws.position(), curState, state.processingState()
            ));
        }

        Integer qty = ingredientQtyRequiredAtStates.get(state.processingState());
        if (qty == null) {
            return OrReason.reasonOnly("The job calls for 0 items at this state: " + state.processingState());
        }
        if (qty == state.ingredientCount()) {
            return OrReason.reasonOnly("Quantity already met");
        }

        Function<ITEM, Boolean> ingredient = ingredientsRequiredAtStates().get(curState);
        Predicate<ITEM> asPred = Util.funcToPredNullable(ingredient);
        NoisyPredicate<ITEM> check = wrapItemCheck(extra, asPred == null ? null : i -> WithReason.bool(
                asPred.test(i),
                "%s is a required ingredient for state %d",
                "%s is not a required ingredient for state %d"
                , i, curState
        ), curState);

        if (qty > 0) {
            WithReason<Boolean> hasMoreResult = hasMore(extra, check, qty - state.ingredientCount());
            if (!hasMoreResult.value) {
                return OrReason.reasonOnly(
                        "There are not enough ingredients. [Wanted %s; %s; Found in Block %d]",
                        qty, hasMoreResult.reason(), state.ingredientCount()
                );
            }
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
            TOWN town = tryInsertItem(extra, this, state, item, check, bp, nextStepWork, nextStepTime,
                    (uxtra, tuwn) -> setHeldItem(uxtra, tuwn, villagerIndex, ii, item.shrink())
            );
            if (town != null) {
                QT.JOB_LOGGER.debug("Villager removed {} from their inventory {}", name, invBefore);

                itemInsertedListener.forEach(v -> v.accept(extra, bp, item));

                Claim claim = claimSpots.apply(extra);
                if (claim != null) {
                    if (getWorkStatuses(extra).claimSpot(bp, claim)) {
                        return OrReason.success(town);
                    }
                    return OrReason.reasonOnly(String.format("Spot cannot be claimed: %s", bp));
                } else {
                    return OrReason.success(town);
                }
            }
        }
        return OrReason.reasonOnly("Not holding a valid item for insertion");
    }

    private NoisyPredicate<ITEM> wrapItemCheck(
            EXTRA extra,
            NoisyPredicate<ITEM> check,
            int state
    ) {
        Integer q = Util.getOrDefault(ingredientQtyRequiredAtStates, state, 0);
        QuantityRequired qr = new QuantityRequired(q);
        return Predicates.applyWrapping(
                getItemInsertionCheckModifiers(
                        extra,
                        specialRules.apply(state),
                        Util.toQuiet(check),
                        qr
                ),
                check
        );
    }

    private WithReason<Boolean> hasMore(
            EXTRA extra,
            NoisyPredicate<ITEM> isCorrectItem,
            int amountNeeded
    ) {
        if (isCorrectItem == null) {
            return new WithReason<>(false, "Check is null"); // TODO: Unit test?
        }
        Collection<ITEM> held = getHeldItems(extra, villagerIndex);
        int numHeld = 0;
        for (ITEM i : held) {
            WithReason<Boolean> test = isCorrectItem.test(i);
            if (!test.value) {
                continue;
            }
            numHeld++;
        }
        if (numHeld >= amountNeeded) {
            return new WithReason<>(true, "Found in inventory: %d", numHeld);
        }

        long foundInTown = 0;
        Map<ITEM, Integer> itemsInTown = getItemsInTownWithoutCustomNBT(extra);
        for (Map.Entry<ITEM, Integer> entry : itemsInTown.entrySet()) {
            WithReason<Boolean> test = isCorrectItem.test(entry.getKey());
            if (test.value) {
                foundInTown = entry.getValue();
                break;
            }
        }

        if (foundInTown >= amountNeeded) {
            return new WithReason<>(true, "Found in town: %s", foundInTown);
        }

        if (foundInTown + numHeld >= amountNeeded) {
            return new WithReason<>(
                    true,
                    "Found in town: %d; Found in inventory: %d",
                    foundInTown, numHeld
            );
        }

        return new WithReason<>(
                false,
                "Found in town: %d; Found in inventory: %d",
                foundInTown, numHeld
        );
    }

    protected abstract Map<ITEM, Integer> getItemsInTownWithoutCustomNBT(EXTRA extra);

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
            NoisyPredicate<ITEM> check,
            POS bp,
            Integer workInNextStep,
            Integer timeInNextStep,
            BiFunction<EXTRA, TOWN, TOWN> shrinkItem
    ) {
        ImmutableWorkStateContainer<POS, TOWN> ws = getWorkStatuses(extra);
        int curValue = oldState.processingState();
        Integer qtyRequired = rules.ingredientQuantityRequiredAtStates()
                                   .getOrDefault(curValue, 0);

        WithReason<Boolean> canDo = check.test(item);
        if (qtyRequired == null) {
            qtyRequired = 0;
        }
        int curCount = oldState.ingredientCount();
        if (canDo.value && curCount > qtyRequired) {
            QT.BLOCK_LOGGER.error(
                    "Somehow exceeded required quantity: can accept up to {}, had {}",
                    qtyRequired,
                    curCount
            );
        }

        int count = curCount + 1;
        boolean shrink = canDo.value && count <= qtyRequired;

        TOWN updatedTown = maybeUpdateBlockState(
                oldState, bp, workInNextStep, timeInNextStep, canDo.value, count, qtyRequired, ws);

        if (shrink) {
            return shrinkItem.apply(extra, updatedTown);
        }
        return updatedTown;
    }

    protected abstract Collection<? extends Function<NoisyPredicate<ITEM>, NoisyPredicate<ITEM>>> getItemInsertionCheckModifiers(
            EXTRA extra,
            Collection<String> activeSpecialRules,
            Predicate<ITEM> originalCheck,
            QuantityRequired qtyRequired
    );

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
