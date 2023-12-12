package ca.bradj.questown.town;

import ca.bradj.questown.QT;
import ca.bradj.questown.town.interfaces.WorkStatusHandle;
import ca.bradj.roomrecipes.core.Room;
import ca.bradj.roomrecipes.core.space.InclusiveSpace;
import ca.bradj.roomrecipes.core.space.Position;
import ca.bradj.roomrecipes.logic.InclusiveSpaces;
import com.google.common.collect.ImmutableMap;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;

public class WorkStatusStore<POS, ITEM, ROOM extends Room, TICK_SOURCE> implements WorkStatusHandle<POS, ITEM> {

    // Work status is generally only stored in this store. However, some
    // blocks support applying the status directly to the block (e.g. for
    // visual indication of progress). This map facilitates that.
    private final Map<POS, Consumer<State>> cascading = new HashMap<>();
    private final BiFunction<ROOM, Position, POS> posFactory;
    private final BiFunction<TICK_SOURCE, POS, Boolean> airCheck;
    private final BiFunction<TICK_SOURCE, POS, @Nullable State> defaultStateFactory;
    private final BiFunction<TICK_SOURCE, POS, @Nullable Consumer<State>> cascadingBlockRevealer;
    private final Consumer<ITEM> shrinker;

    public record State(
            int processingState,
            int ingredientCount,
            int workLeft
    ) {

        public State setProcessing(int s) {
            return new State(s, ingredientCount, workLeft);
        }

        public State setWorkLeft(int newVal) {
            return new State(processingState, ingredientCount, newVal);
        }

        public State setCount(int count) {
            return new State(processingState, count, workLeft);
        }

        @Override
        public String toString() {
            return "State{" +
                    "processingState=" + processingState +
                    ", ingredientCount=" + ingredientCount +
                    ", workLeft=" + workLeft +
                    '}';
        }

        public String toShortString() {
            return "[" +
                    "state=" + processingState +
                    ", ingCount=" + ingredientCount +
                    ", workLeft=" + workLeft +
                    ']';
        }

    }

    private final HashSet<ROOM> rooms = new HashSet<>();

    private final HashMap<POS, State> jobStatuses = new HashMap<>();
    private final HashMap<POS, Integer> timeJobStatuses = new HashMap<>();
    int curIdx = 0;

    public WorkStatusStore(
            BiFunction<ROOM, Position, POS> posFactory,
            BiFunction<TICK_SOURCE, POS, Boolean> airCheck,
            BiFunction<TICK_SOURCE, POS, @Nullable State> defaultStateFactory,
            BiFunction<TICK_SOURCE, POS, @Nullable Consumer<State>> cascadingBlockRevealer,
            Consumer<ITEM> shrinker
    ) {
        this.posFactory = posFactory;
        this.airCheck = airCheck;
        this.defaultStateFactory = defaultStateFactory;
        this.cascadingBlockRevealer = cascadingBlockRevealer;
        this.shrinker = shrinker;
    }

    @Override
    public State getJobBlockState(POS bp) {
        return jobStatuses.get(bp);
    }

    @Override
    public void setJobBlockState(
            POS bp,
            State bs
    ) {
        jobStatuses.put(bp, bs);
        QT.FLAG_LOGGER.debug("Job state set to {} at {}", bs.toShortString(), bp);

        if (cascading.containsKey(bp)) {
            cascading.get(bp).accept(bs);
        }
    }

    @Override
    public void setJobBlockStateWithTimer(
            POS bp,
            State bs,
            int ticksToNextState
    ) {
        setJobBlockState(bp, bs);
        Integer cur = this.timeJobStatuses.get(bp);
        if (cur != null && cur > 0) {
            QT.FLAG_LOGGER.error("Clobbered time on block at {} from {} to {}", bp, cur, ticksToNextState);
        }

        QT.BLOCK_LOGGER.debug("Timer added to {} at {} ({} to next state)", bs.toShortString(), bp, ticksToNextState);
        this.timeJobStatuses.put(bp, ticksToNextState);
    }

    @Override
    public @Nullable Integer getTimeToNextState(POS bp) {
        return timeJobStatuses.get(bp);
    }

    public interface InsertionRules<ITEM> {

        Map<Integer, Function<ITEM, Boolean>> ingredientsRequiredAtStates();

        Map<Integer, Integer> ingredientQuantityRequiredAtStates();

    }

    @Override
    public boolean tryInsertItem(
            InsertionRules<ITEM> rules,
            ITEM item,
            POS bp,
            int workToNextStep,
            int timeToNextStep
    ) {
        State oldState = getJobBlockState(bp);
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
            this.shrinker.accept(item);
            int count = curCount + 1;
            State blockState = oldState.setCount(count);
            if (timeToNextStep > 0) {
                setJobBlockStateWithTimer(bp, blockState, timeToNextStep);
            } else {
                setJobBlockState(bp, blockState);
            }
            if (count < qtyRequired) {
                return true;
            }

            if (oldState.workLeft == 0) {
                int val = curValue + 1;
                blockState = blockState.setProcessing(val);
                blockState = blockState.setWorkLeft(workToNextStep);
                blockState = blockState.setCount(0);
                if (timeToNextStep > 0) {
                    setJobBlockStateWithTimer(bp, blockState, timeToNextStep);
                } else {
                    setJobBlockState(bp, blockState);
                }
            }
            return true;
        }
        return false;
    }

    @Override
    public boolean canInsertItem(
            ITEM item,
            POS bp
    ) {
        return jobStatuses.containsKey(bp);
    }

    public void tick(
            TICK_SOURCE tickSource,
            Collection<ROOM> allRooms
    ) {
        rooms.addAll(allRooms);

        if (rooms.isEmpty()) {
            return;
        }

        curIdx = (curIdx + 1) % rooms.size();

        this.doTick(tickSource, (ROOM) rooms.toArray()[curIdx]);
    }

    private void doTick(
            TICK_SOURCE tickSource,
            ROOM o
    ) {
        timeJobStatuses.forEach((k, v) -> timeJobStatuses.compute(k, (kk, vv) -> vv == null ? null : vv - 1));
        ImmutableMap.copyOf(timeJobStatuses)
                .entrySet()
                .stream()
                .filter((e) -> Integer.valueOf(0).equals(e.getValue()))
                .forEach(
                        e -> {
                            QT.BLOCK_LOGGER.debug("Timer at {} expired. Moving to next state", e.getKey());
                            jobStatuses.compute(e.getKey(), (k, v) -> v.setProcessing(v.processingState + 1));
                            timeJobStatuses.remove(e.getKey());
                        }
                );

        for (InclusiveSpace s : o.getSpaces()) {
            for (Position p : InclusiveSpaces.getAllEnclosedPositions(s)) {
                POS pp = posFactory.apply(o, p);
                if (jobStatuses.containsKey(pp)) {
                    if (airCheck.apply(tickSource, pp)) {
                        QT.BLOCK_LOGGER.debug("Block is gone from {}. Clearing status.", pp);
                        jobStatuses.remove(pp);
                    }
                    continue;
                }
                State def = this.defaultStateFactory.apply(tickSource, pp);
                if (def != null) {
                    jobStatuses.put(pp, def);
                }

                @Nullable Consumer<State> cas = cascadingBlockRevealer.apply(tickSource, pp);
                if (cas != null) {
                    cascading.put(pp, cas);
                }
            }
        }
    }
}
