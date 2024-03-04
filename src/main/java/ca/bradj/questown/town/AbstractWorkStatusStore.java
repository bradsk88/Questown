package ca.bradj.questown.town;

import ca.bradj.questown.QT;
import ca.bradj.questown.town.interfaces.WorkStatusHandle;
import ca.bradj.roomrecipes.core.Room;
import ca.bradj.roomrecipes.core.space.InclusiveSpace;
import ca.bradj.roomrecipes.core.space.Position;
import ca.bradj.roomrecipes.logic.InclusiveSpaces;
import com.google.common.collect.ImmutableMap;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

public abstract class AbstractWorkStatusStore<POS, ITEM, ROOM extends Room, TICK_SOURCE> implements WorkStatusHandle<POS, ITEM> {

    // Work status is generally only stored in this store. However, some
    // blocks support applying the status directly to the block (e.g. for
    // visual indication of progress). This map facilitates that.
    private final Map<POS, Consumer<State>> cascading = new HashMap<>();
    private final BiFunction<ROOM, Position, Collection<POS>> posFactory;
    private final BiFunction<TICK_SOURCE, POS, Boolean> airCheck;
    private final BiFunction<TICK_SOURCE, POS, @Nullable State> defaultStateFactory;
    private final BiFunction<TICK_SOURCE, POS, @Nullable Consumer<State>> cascadingBlockRevealer;

    public static class State {
        private final int processingState;
        private final int ingredientCount;

        // IMPORTANT: This value should only be modified by setWorkLeft and
        // internalSetWorkLeft so that the 10x scaling is preserved.
        private final int workLeft;

        private State(
                int processingState,
                int ingredientCounts,
                int workLeft
        ) {
            this.processingState = processingState;
            this.ingredientCount = ingredientCounts;
            this.workLeft = workLeft;
        }

        public static State fresh() {
            return new State(0, 0, 0);
        }

        public static State freshAtState(int s) {
            return new State(s, 0, 0);
        }

        public State setProcessing(int s) {
            return new State(s, ingredientCount, workLeft);
        }

        public State setWorkLeft(int newVal) {
            return new State(processingState, ingredientCount, newVal * 10);
        }

        private State internalSetWorkLeft(int newVal) {
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
                    ", workLeft=" + (0.1f * workLeft) +
                    '}';
        }

        public String toShortString() {
            return "[" +
                    "state=" + processingState +
                    ", ingCount=" + ingredientCount +
                    ", workLeft=" + (0.1f * workLeft) +
                    ']';
        }

        public State incrProcessing() {
            return setProcessing(processingState + 1);
        }

        public State incrIngredientCount() {
            return setCount(ingredientCount + 1);
        }

        public State decrWork(
                int amountOf10
        ) {
            if (amountOf10 > 10 || amountOf10 < 1) {
                throw new IllegalArgumentException("Only 1-10 are allowed (got: " + amountOf10 + ")");
            }
            return internalSetWorkLeft(Math.max(workLeft - amountOf10, 0));
        }

        public boolean isFresh() {
            return fresh().equals(this);
        }

        public int processingState() {
            return processingState;
        }

        public int workLeft() {
            return (int) (0.1f * workLeft);
        }

        public int ingredientCount() {
            return ingredientCount;
        }
    }

    private final HashSet<ROOM> rooms = new HashSet<>();

    private final HashMap<POS, State> jobStatuses = new HashMap<>();
    private final HashMap<POS, Integer> timeJobStatuses = new HashMap<>();
    private final HashMap<POS, Claim> claims = new HashMap<>();

    int curIdx = 0;

    public AbstractWorkStatusStore(
            BiFunction<ROOM, Position, Collection<POS>> posFactory,
            BiFunction<TICK_SOURCE, POS, Boolean> airCheck,
            BiFunction<TICK_SOURCE, POS, @Nullable State> defaultStateFactory,
            BiFunction<TICK_SOURCE, POS, @Nullable Consumer<State>> cascadingBlockRevealer
    ) {
        this.posFactory = posFactory;
        this.airCheck = airCheck;
        this.defaultStateFactory = defaultStateFactory;
        this.cascadingBlockRevealer = cascadingBlockRevealer;
    }

    @Override
    public State getJobBlockState(POS bp) {
        return jobStatuses.get(bp);
    }

    @Override
    public Boolean setJobBlockState(
            POS bp,
            State bs
    ) {
        modifyJobBlockState(bp, (p, s) -> bs);
        return true;
    }

    private void modifyJobBlockState(
            POS pos,
            BiFunction<POS, State, State> mutator
    ) {
        State newV = jobStatuses.compute(pos, mutator);
        QT.FLAG_LOGGER.debug("Job state set to {} at {}", newV.toShortString(), pos);
        if (cascading.containsKey(pos)) {
            cascading.get(pos).accept(newV);
        }
    }

    @Override
    public Boolean setJobBlockStateWithTimer(
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
        return true;
    }

    @Override
    public Boolean clearState(POS bp) {
        this.timeJobStatuses.remove(bp);
        this.jobStatuses.remove(bp);
        return true;
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
        claims.forEach((k, v) -> claims.compute(k, (kk, vv) -> {
            if (vv == null) {
                return null;
            }
            return vv.ticked();
        }));
        ImmutableMap.copyOf(timeJobStatuses)
                .entrySet()
                .stream()
                .filter((e) -> Integer.valueOf(0).equals(e.getValue()))
                .forEach(
                        e -> {
                            QT.BLOCK_LOGGER.debug("Timer at {} expired. Moving to next state", e.getKey());
                            modifyJobBlockState(
                                    e.getKey(),
                                    (pos, state) -> state.setProcessing(state.processingState + 1)
                            );
                            timeJobStatuses.remove(e.getKey());
                        }
                );

        for (InclusiveSpace s : o.getSpaces()) {
            for (Position p : InclusiveSpaces.getAllEnclosedPositions(s)) {
                posFactory.apply(o, p).forEach(pp -> {
                    if (jobStatuses.containsKey(pp)) {
                        if (airCheck.apply(tickSource, pp)) {
                            QT.BLOCK_LOGGER.debug("Block is gone from {}. Clearing status.", pp);
                            jobStatuses.remove(pp);
                        }
                        return;
                    }
                    State def = this.defaultStateFactory.apply(tickSource, pp);
                    if (def != null) {
                        jobStatuses.put(pp, def);
                    }

                    @Nullable Consumer<State> cas = cascadingBlockRevealer.apply(tickSource, pp);
                    if (cas != null) {
                        cascading.put(pp, cas);
                        cas.accept(def);
                    }
                });
            }
        }
    }

    @Override
    public ImmutableMap<POS, State> getAll() {
        return ImmutableMap.copyOf(jobStatuses);
    }

    @Override
    public boolean claimSpot(
            POS bp,
            Claim claim
    ) {
        if (doClaimSpot(bp, claim)) {
            QT.JOB_LOGGER.debug("Spot {} claimed: {}", bp, claim);
            return true;
        }
        return false;
    }

    private boolean doClaimSpot(
            POS bp,
            Claim claim
    ) {
        Claim c = claims.get(bp);
        if (c == null) {
            claims.put(bp, claim);
            return true;
        }
        if (c.owner().equals(claim.owner())) {
            claims.put(bp, claim);
            return true;
        }
        return false;
    }

    @Override
    public void clearClaim(POS position) {
        Claim claim = claims.remove(position);
        QT.JOB_LOGGER.debug("Claim {} released: {}", position, claim);
    }

    @Override
    public boolean canClaim(POS position, Supplier<Claim> makeClaim) {
        Claim prevClaim = claims.get(position);
        if (prevClaim == null) {
            return true;
        }
        Claim newClaim = makeClaim.get();
        return prevClaim.owner().equals(newClaim.owner());
    }
}
