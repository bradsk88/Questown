package ca.bradj.questown.town;

import ca.bradj.questown.QT;
import ca.bradj.questown.core.Config;
import ca.bradj.questown.gui.ItemEconomicsData;
import ca.bradj.questown.jobs.Signals;
import com.google.common.collect.ImmutableList;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Stack;
import java.util.function.Supplier;

public abstract class AbstractTownFlagTicker<TICK_DATA, VILLAGERS> {
    private boolean stopped;
    protected boolean everScanned = false;
    boolean debugMode;
    private @Nullable Supplier<Boolean> debugTask;
    private boolean isMorning;
    private final Stack<Long> mornings = new Stack<>();
    private final ArrayList<Integer> times = new ArrayList<>();

    protected void tick(
            TICK_DATA data
    ) {
        // Profiling wraps every exit path, not just the full-tick one. The heavy work below is
        // throttled to every FlagTickInterval-th tick, but updateStoredData (and its town-wide
        // container scan) runs on ALL of them — measuring only the full ticks would hide the
        // per-tick cost entirely.
        long start = System.nanoTime();
        try {
            tickInner(data);
        } finally {
            profileTick(start);
        }
    }

    private void tickInner(
            TICK_DATA data
    ) {
        if (runAnyInitializers(data)) {
            // A flag that is initializing (after a reload) is alive: its sub-blocks self-destruct
            // when not ticked, so keep them fed while the initializers run.
            notifySubBlocksOfTick(data);
            return;
        }

        Collection<VILLAGERS> villagers = getVillagers(data);

        boolean alreadyStopped = stopped;
        stopped = allPlayersLeftArea(data, !alreadyStopped);
        if (stopped) {
            if (alreadyStopped) {
                return;
            }
            storeInactiveState(data);
        }

        // Must tick sub-blocks even with debug mode enabled,
        // because non-ticked sub-blocks will self-destruct.
        if (!stopped) {
            notifySubBlocksOfTick(data);
        }

        if (runDebugTask()) {
            return;
        }

        Signals.DayTime dayTime = getDayTime(data);
        Signals signals = Signals.fromDayTime(dayTime);

        handleIfNewMorning(data, signals);

        boolean[] stateChanged = new boolean[1];
        TickProfile.INSTANCE.phase("updateStoredData", () -> stateChanged[0] = updateStoredData(data));

        if (stateChanged[0]) {
            TickProfile.INSTANCE.phase("handleNewStoredData", () -> handleNewStoredData(data));
        }

        TickProfile.INSTANCE.phase("workHandle", () -> tickWorkHandle(data));
        TickProfile.INSTANCE.phase("quests", () -> tickQuests(data));
        TickProfile.INSTANCE.phase("biomes", () -> tickBiomesHandle(data));
        TickProfile.INSTANCE.phase("healing", () -> tickHealingHandle(data));
        TickProfile.INSTANCE.phase("possibleWork", () -> tickPossibleWorkHandle(data));
        TickProfile.INSTANCE.phase("roomsHandle", () -> tickRoomsHandle(data));

        if (!isFlagTick(data)) {
            return;
        }

        TickProfile.INSTANCE.phase("updateWorkStatuses", () -> updateWorkStatuses(data));

        TickProfile.INSTANCE.phase("asapRewards", () -> tickASAPRewards(data));
        TickProfile.INSTANCE.phase("pois", () -> tickPOIs(data));

        if (signals == Signals.NIGHT || signals == Signals.EVENING) {
            tickEvening(data);
        }

        if ((signals == Signals.NIGHT || signals == Signals.EVENING) && !villagers.isEmpty()) {
            triggerFirstNightAdvancement(data);
        }

        tickVillagers(data, signals);

        if (applyNewEconomicsData(data)) {
            if (getTownLevelItemEconomics(data).stream().anyMatch(v -> v.timesNeeded() > 4)) {
                triggerUnmetNeedsAdvancement(data);
            }
        }

        everScanned = true;
    }

    protected abstract void triggerUnmetNeedsAdvancement(TICK_DATA data);

    protected abstract ImmutableList<ItemEconomicsData> getTownLevelItemEconomics(TICK_DATA data);

    protected abstract boolean applyNewEconomicsData(TICK_DATA data);

    protected abstract void tickVillagers(
            TICK_DATA data,
            Signals signals
    );

    protected abstract void triggerFirstNightAdvancement(TICK_DATA data);

    protected abstract void tickEvening(TICK_DATA data);

    protected abstract void tickPOIs(TICK_DATA data);

    protected abstract void tickASAPRewards(TICK_DATA data);

    protected abstract void updateWorkStatuses(TICK_DATA data);

    protected abstract boolean isFlagTick(TICK_DATA data);

    protected abstract void tickRoomsHandle(TICK_DATA data);

    protected abstract void tickPossibleWorkHandle(TICK_DATA data);

    protected abstract void tickHealingHandle(TICK_DATA data);

    protected abstract void tickBiomesHandle(TICK_DATA data);

    protected abstract void tickQuests(TICK_DATA data);

    protected abstract void tickWorkHandle(TICK_DATA data);

    protected abstract void handleNewStoredData(TICK_DATA data);

    protected abstract boolean updateStoredData(TICK_DATA data);

    private void handleIfNewMorning(
            TICK_DATA data,
            Signals signals
    ) {
        detectMorning(data, signals);

        if (!mornings.empty()) {
            runMorning(data, mornings.pop());
        }
    }

    /**
     * Run the morning pass and mark this morning as consumed. The ticker's
     * own detection (handleIfNewMorning) uses this; the campfire-sleep wake
     * path (TownFlags.onSleepFinished) must use it too - calling the
     * abstract onMorning directly leaves isMorning unset, so the ticker's
     * detection re-runs the whole morning pass in the same window
     * (duplicate BOP apply / duplicate jobs.changed broadcast, 2026-10-15
     * playtest).
     */
    public void runMorning(
            TICK_DATA data,
            long newTime
    ) {
        isMorning = true;
        onMorning(data, newTime);
    }

    /**
     * The campfire-sleep wake's morning pass. It runs on {@link #getTick} (game time), the same
     * clock as the ticker's own morning detection, never the wake event's new daytime: the morning
     * pass stamps SLEEPLESS expiry and last-downtime ticks that are read against game time, and a
     * sleep skip advances daytime but not game time (r16 #6: SLEEPLESS lasted ~44 500 ticks
     * instead of 23 000). Pinned by {@code SleepMorningClockTest}.
     */
    public void runSleptMorning(TICK_DATA data) {
        runMorning(data, getTick(data));
    }

    protected abstract void onMorning(
            TICK_DATA data,
            Long pop
    );

    private void detectMorning(
            TICK_DATA data,
            Signals signals
    ) {
        if (signals == Signals.MORNING) {
            if (!isMorning) {
                isMorning = true;
                onMorningDetected(getTick(data));
            }
        } else {
            if (isMorning) {
                isMorning = false;
            }
        }
    }

    protected abstract long getTick(TICK_DATA data);

    protected abstract Signals.DayTime getDayTime(TICK_DATA data);

    private boolean runDebugTask() {
        if (!this.debugMode) {
            return false;
        }
        if (debugTask == null) {
            // While Debug Mode is enabled, a null debug task is
            // considered "running the task"
            return true;
        }
        boolean done = debugTask.get();
        if (done) {
            debugTask = null;
        }
        return true;
    }

    protected abstract void notifySubBlocksOfTick(TICK_DATA data);

    protected abstract void storeInactiveState(TICK_DATA data);

    protected abstract boolean allPlayersLeftArea(
            TICK_DATA data,
            boolean playersNearbyOnPrevTick
    );

    private void onMorningDetected(long newTime) {
        this.mornings.push(newTime);
        // IMPORTANT: DO NOTHING ELSE IN THIS FUNCTION
        // Adding logic here may cause the game to lock up.
        // Do morning logic via morningTick().
    }

    protected abstract ImmutableList<VILLAGERS> getVillagers(TICK_DATA data);

    protected abstract boolean runAnyInitializers(TICK_DATA data);

    public boolean startDebugTask(Supplier<Boolean> debugTask) {
        if (!this.debugMode) {
            return false;
        }
        this.debugTask = debugTask;
        return true;
    }

    private void profileTick(long start) {
        long elapsedNanos = System.nanoTime() - start;

        TickProfile.INSTANCE.record(elapsedNanos);

        if (Config.TICK_SAMPLING_RATE.get() > 0) {
            times.add((int) (elapsedNanos / 1000));

            if (times.size() > Config.TICK_SAMPLING_RATE.get()) {
                double tl = times.stream().mapToInt(Integer::intValue).average().orElse(0);
                QT.PROFILE_LOGGER.info("Average tick length: {}us", tl);
                times.clear();
            }
        }
    }

    public void toggleDebugMode() {
        this.debugMode = !this.debugMode;
    }

    public boolean isDebugEnabled() {
        return debugMode;
    }
}
