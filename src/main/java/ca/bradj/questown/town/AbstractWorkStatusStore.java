package ca.bradj.questown.town;

import ca.bradj.questown.QT;
import ca.bradj.questown.logic.PredicateCollection;
import ca.bradj.questown.town.interfaces.WorkStatusHandle;
import ca.bradj.questown.town.workstatus.State;
import ca.bradj.roomrecipes.core.Room;
import ca.bradj.roomrecipes.core.space.InclusiveSpace;
import ca.bradj.roomrecipes.core.space.Position;
import ca.bradj.roomrecipes.logic.InclusiveSpaces;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import org.jetbrains.annotations.Nullable;

import java.util.*;
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
    private final List<ROOM> roomRotation = new ArrayList<>();
    private final Map<ROOM, Collection<POS>> scannablePositions = new HashMap<>();

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
        Collection<ROOM> newRooms = registerNewRooms(allRooms);

        if (roomRotation.isEmpty()) {
            return;
        }

        // Store-global timers decay once per tick, no matter how many rooms were scanned.
        advanceBlocksWithExpiredTimers(decayTimersAndCollectExpired(ticksSinceLast));

        newRooms.forEach(room -> scanRoom(tickSource, room));

        ROOM roomToScan = nextRoomInRotation();
        if (newRooms.contains(roomToScan)) {
            return;
        }
        scanRoom(tickSource, roomToScan);
    }

    private Collection<ROOM> registerNewRooms(Collection<ROOM> allRooms) {
        Collection<ROOM> newRooms = null;
        for (ROOM room : allRooms) {
            if (!rooms.add(room)) {
                continue;
            }
            roomRotation.add(room);
            if (newRooms == null) {
                newRooms = new ArrayList<>();
            }
            newRooms.add(room);
        }
        return newRooms == null ? ImmutableList.of() : newRooms;
    }

    private ROOM nextRoomInRotation() {
        curIdx = (curIdx + 1) % roomRotation.size();
        return roomRotation.get(curIdx);
    }

    private Collection<POS> decayTimersAndCollectExpired(long ticksSinceLast) {
        decayClaims(ticksSinceLast);

        Collection<POS> expired = null;
        for (Map.Entry<POS, Long> e : timeJobStatuses.entrySet()) {
            if (e.getValue() == null) {
                continue;
            }
            long remaining = e.getValue() - ticksSinceLast;
            e.setValue(remaining);
            if (remaining > 0) {
                continue;
            }
            if (expired == null) {
                expired = new ArrayList<>();
            }
            expired.add(e.getKey());
        }
        return expired == null ? ImmutableList.of() : expired;
    }

    /**
     * Advance every claim by the elapsed game-tick delta and drop the ones that run out. A claim
     * is the catch-all release for a work spot: nothing clears it when its owner dies, unloads,
     * changes jobs, or gets stuck (only the work-cycle reset and the morning plate reset do), so
     * without a TTL an orphaned claim would block the spot from every other townie for the rest
     * of the world's life — and these servers rarely restart. Active townies refresh the claim
     * on every item insert, so in practice only abandoned claims expire. The delta is the same
     * {@code ticksSinceLast} the timers use, so {@code BLOCK_CLAIMS_TICK_LIMIT} means game ticks,
     * not flag ticks.
     */
    private void decayClaims(long ticksSinceLast) {
        Iterator<Map.Entry<POS, Claim>> it = claims.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<POS, Claim> e = it.next();
            Claim current = e.getValue();
            if (current == null) {
                it.remove();
                continue;
            }
            Claim next = current.ticked(ticksSinceLast);
            if (next == null) {
                it.remove();
                debugLogger.log("Claim at {} expired (TTL ran out) and was released", e.getKey());
            } else {
                e.setValue(next);
            }
        }
    }

    private void advanceBlocksWithExpiredTimers(Collection<POS> expired) {
        for (POS pos : expired) {
            debugLogger.log("Timer at {} expired. Moving to next state", pos);
            modifyJobBlockState(
                    pos,
                    (p, state) -> {
                        if (state == null) {
                            QT.logBug("State was null after timer expired");
                            return State.fresh();
                        }
                        return state.incrProcessing();
                    }
            );
            timeJobStatuses.remove(pos);
        }
    }

    private void scanRoom(
            TICK_SOURCE tickSource,
            ROOM room
    ) {
        for (POS pp : scannablePositionsOf(room)) {
            if (jobStatuses.containsKey(pp)) {
                if (airCheck.apply(tickSource, pp)) {
                    debugLogger.log("Block is gone from {}. Clearing status.", pp);
                    jobStatuses.remove(pp);
                }
                continue;
            }
            State def = this.defaultStateFactory.apply(tickSource, pp);
            if (def != null) {
                jobStatuses.put(pp, def);
            }

            @Nullable Function<State, Boolean> cas = cascadingBlockRevealer.apply(tickSource, pp);
            if (cas == null) {
                continue;
            }
            cascading.put(pp, cas);
            if (!cas.apply(def == null ? State.fresh() : def)) {
                cascading.remove(pp);
            }
        }
    }

    /**
     * The positions a room covers never change, so they are computed once and reused. This
     * avoids re-walking (and re-allocating) every enclosed position on every scan.
     */
    private Collection<POS> scannablePositionsOf(ROOM room) {
        return scannablePositions.computeIfAbsent(room, this::computeScannablePositions);
    }

    private Collection<POS> computeScannablePositions(ROOM room) {
        Set<POS> positions = new LinkedHashSet<>();
        for (InclusiveSpace s : room.getSpaces()) {
            for (Position p : InclusiveSpaces.getAllEnclosedPositions(s)) {
                positions.addAll(posFactory.apply(room, p));
            }
        }
        return ImmutableList.copyOf(positions);
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
