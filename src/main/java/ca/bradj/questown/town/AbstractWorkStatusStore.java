package ca.bradj.questown.town;

import ca.bradj.questown.QT;
import ca.bradj.questown.logic.PredicateCollection;
import ca.bradj.questown.town.interfaces.WorkStatusHandle;
import ca.bradj.questown.town.workstatus.State;
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
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;

public abstract class AbstractWorkStatusStore<POS, ITEM, ROOM extends Room, TICK_SOURCE> implements WorkStatusHandle<POS, ITEM> {

    @FunctionalInterface
    public interface DebugLogger {
        void log(String message, Object... params);
    }

    // Work status is generally only stored in this store. However, some
    // blocks support applying the status directly to the block (e.g. for
    // visual indication of progress). This map facilitates that.
    private final Map<POS, Function<State, Boolean>> cascading = new HashMap<>();
    private final BiFunction<ROOM, Position, Collection<POS>> posFactory;
    private final BiFunction<TICK_SOURCE, POS, Boolean> airCheck;
    private final BiFunction<TICK_SOURCE, POS, @Nullable State> defaultStateFactory;
    private final BiFunction<TICK_SOURCE, POS, @Nullable Function<State, Boolean>> cascadingBlockRevealer;

    private final HashSet<ROOM> rooms = new HashSet<>();

    private final HashMap<POS, State> jobStatuses = new HashMap<>();
    private final HashMap<POS, Long> timeJobStatuses = new HashMap<>();
    private final HashMap<POS, Claim> claims = new HashMap<>();

    private final DebugLogger debugLogger;

    int curIdx = 0;

    public AbstractWorkStatusStore(
            BiFunction<ROOM, Position, Collection<POS>> posFactory,
            BiFunction<TICK_SOURCE, POS, Boolean> airCheck,
            BiFunction<TICK_SOURCE, POS, @Nullable State> defaultStateFactory,
            BiFunction<TICK_SOURCE, POS, @Nullable Function<State, Boolean>> cascadingBlockRevealer
    ) {
        this(posFactory, airCheck, defaultStateFactory, cascadingBlockRevealer, null);
    }

    public AbstractWorkStatusStore(
            BiFunction<ROOM, Position, Collection<POS>> posFactory,
            BiFunction<TICK_SOURCE, POS, Boolean> airCheck,
            BiFunction<TICK_SOURCE, POS, @Nullable State> defaultStateFactory,
            BiFunction<TICK_SOURCE, POS, @Nullable Function<State, Boolean>> cascadingBlockRevealer,
            @Nullable DebugLogger debugLogger
    ) {
        this.posFactory = posFactory;
        this.airCheck = airCheck;
        this.defaultStateFactory = defaultStateFactory;
        this.cascadingBlockRevealer = cascadingBlockRevealer;
        this.debugLogger = debugLogger != null ? debugLogger : (m, p) -> {};
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
        debugLogger.log("Job state set to {} at {}", newV.toShortString(), pos);
        if (cascading.containsKey(pos)) {
            if (!cascading.get(pos).apply(newV)) {
                cascading.remove(pos);
            }
        }
    }

    @Override
    public Boolean setJobBlockStateWithTimer(
            POS bp,
            State bs,
            int ticksToNextState
    ) {
        setJobBlockState(bp, bs);
        Long cur = this.timeJobStatuses.get(bp);
        if (cur != null && cur > 0) {
            QT.FLAG_LOGGER.error("Clobbered time on block at {} from {} to {}", bp, cur, ticksToNextState);
        }

        debugLogger.log("Timer added to {} at {} ({} to next state)", bs.toShortString(), bp, ticksToNextState);
        this.timeJobStatuses.put(bp, (long) ticksToNextState);
        return true;
    }

    @Override
    public Boolean clearState(POS bp) {
        this.timeJobStatuses.remove(bp);
        this.jobStatuses.remove(bp);
        debugLogger.log("Removed state from {}", bp);
        return true;
    }

    @Override
    public @Nullable Integer getTimeToNextState(POS bp) {
        Long value = timeJobStatuses.get(bp);
        return value == null ? null : Math.toIntExact(value);
    }

    @Override
    public void drainAllTimers() {
        this.timeJobStatuses.keySet().forEach(k -> this.timeJobStatuses.put(k, 1L));
    }

    public interface InsertionRules<ITEM> {

        @Nullable PredicateCollection<ITEM, ?> getIngredientsRequiredAtState(Integer state);

        @Nullable Integer getIngredientQuantityRequiredAtState(int state, @Nullable Integer orDefault);

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
            Collection<ROOM> allRooms,
            long ticksSinceLast
    ) {
        // Track rooms that were just initialized this tick (to avoid double-ticking)
        Set<ROOM> justInitialized = new HashSet<>();

        // Initialize work states for new rooms immediately
        for (ROOM room : allRooms) {
            if (!rooms.contains(room)) {
                rooms.add(room);
                justInitialized.add(room);
                // Initialize work states for new room right away
                this.doTick(tickSource, room, ticksSinceLast);
            }
        }

        if (rooms.isEmpty()) {
            return;
        }

        curIdx = (curIdx + 1) % rooms.size();

        ROOM roomToTick = (ROOM) rooms.toArray()[curIdx];
        // Skip if this room was just initialized (already ticked above)
        if (!justInitialized.contains(roomToTick)) {
            this.doTick(tickSource, roomToTick, ticksSinceLast);
        }
    }

    private void doTick(
            TICK_SOURCE tickSource,
            ROOM o,
            long ticksSinceLast
    ) {
        timeJobStatuses.forEach((k, v) -> timeJobStatuses.compute(k, (kk, vv) -> vv == null ? null : vv - ticksSinceLast));
        claims.forEach((k, v) -> claims.compute(k, (kk, vv) -> {
            if (vv == null) {
                return null;
            }
            return vv.ticked();
        }));
        ImmutableMap.copyOf(timeJobStatuses)
                .entrySet()
                .stream()
                .filter((e) -> e.getValue() <= 0)
                .forEach(
                        e -> {
                            debugLogger.log("Timer at {} expired. Moving to next state", e.getKey());
                            modifyJobBlockState(
                                    e.getKey(),
                                    (pos, state) -> {
                                        if (state == null) {
                                            QT.logBug("State was null after timer expired");
                                            return State.fresh();
                                        }
                                        return state.incrProcessing();
                                    }
                            );
                            timeJobStatuses.remove(e.getKey());
                        }
                );

        for (InclusiveSpace s : o.getSpaces()) {
            for (Position p : InclusiveSpaces.getAllEnclosedPositions(s)) {
                posFactory.apply(o, p).forEach(pp -> {
                    if (jobStatuses.containsKey(pp)) {
                        if (airCheck.apply(tickSource, pp)) {
                            debugLogger.log("Block is gone from {}. Clearing status.", pp);
                            jobStatuses.remove(pp);
                        }
                        return;
                    }
                    State def = this.defaultStateFactory.apply(tickSource, pp);
                    if (def != null) {
                        jobStatuses.put(pp, def);
                    }

                    @Nullable Function<State, Boolean> cas = cascadingBlockRevealer.apply(tickSource, pp);
                    if (cas != null) {
                        cascading.put(pp, cas);
                        if (!cas.apply(def == null ? State.fresh() : def)) {
                            cascading.remove(pp);
                        }
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
            debugLogger.log("Spot {} claimed: {}", bp, claim);
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
        debugLogger.log("Claim {} released: {}", position, claim);
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
