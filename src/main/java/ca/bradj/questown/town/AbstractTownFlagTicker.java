package ca.bradj.questown.town;

import ca.bradj.questown.QT;
import ca.bradj.questown.core.Config;
import ca.bradj.questown.core.Pair;
import ca.bradj.questown.gui.ItemEconomicsData;
import ca.bradj.questown.jobs.Signals;
import ca.bradj.questown.town.quests.Completable;
import ca.bradj.questown.town.quests.Reward;
import com.google.common.collect.ImmutableList;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Stack;
import java.util.function.Supplier;

public abstract class AbstractTownFlagTicker<TICK_DATA, VILLAGERS> {
    private int ticksWithoutQuests;
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
        if (runAnyInitializers(data)) {
            return;
        }

        Collection<VILLAGERS> villagers = getVillagers(data);
        ensureCompletableQuestsExist(data, villagers);

        boolean alreadyStopped = stopped;
        stopped = allPlayersLeftArea(data, !alreadyStopped);
        if (stopped) {
            if (alreadyStopped) {
                return;
            }
            storeSleepingState(data);
        }

        long start = System.currentTimeMillis();

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

        boolean stateChanged = updateStoredData(data);

        if (stateChanged) {
            handleNewStoredData(data);
        }

        tickWorkHandle(data);
        tickQuests(data);
        tickBiomesHandle(data);
        tickHealingHandle(data);
        tickPossibleWorkHandle(data);
        tickRoomsHandle(data);

        if (!isFlagTick(data)) {
            return;
        }

        updateWorkStatuses(data);

        tickASAPRewards(data);
        tickPOIs(data);

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

        profileTick(start);
    }

    protected abstract void triggerUnmetNeedsAdvancement(TICK_DATA data);

    protected abstract ImmutableList<ItemEconomicsData> getTownLevelItemEconomics(TICK_DATA data);

    protected abstract boolean applyNewEconomicsData(TICK_DATA data);

    protected abstract void tickVillagers(
            TICK_DATA data,
            Signals signals
    );

    protected abstract void triggerFirstNightAdvancement(TICK_DATA data);

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
            onMorning(data, mornings.pop());
        }
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

    protected abstract void storeSleepingState(TICK_DATA data);

    protected abstract boolean allPlayersLeftArea(
            TICK_DATA data,
            boolean playersNearbyOnPrevTick
    );

    private void ensureCompletableQuestsExist(
            TICK_DATA data,
            Collection<VILLAGERS> villagers
    ) {
        // We use a loop to ensure the more expensive "quests" check is only
        // run if there is at least one villager in the town.
        for (VILLAGERS villager : villagers) {
            if (!isMissingCompletableQuests(data)) {
                ticksWithoutQuests = 0;
                break;
            }
            boolean allowingForBuffer = ticksWithoutQuests < 500;
            if (allowingForBuffer) {
                ticksWithoutQuests++;
                break;
            }
            addQuests(data, villager);
            ticksWithoutQuests = 0;
            break; // We only want to add a quest batch for the first villager
        }
    }

    private void onMorningDetected(long newTime) {
        this.mornings.push(newTime);
        // IMPORTANT: DO NOTHING ELSE IN THIS FUNCTION
        // Adding logic here may cause the game to lock up.
        // Do morning logic via morningTick().
    }

    protected abstract void addQuests(
            TICK_DATA data,
            VILLAGERS v
    );

    private boolean isMissingCompletableQuests(TICK_DATA e) {
        Collection<Pair<Completable, Reward>> all = getAllQuestsWithRewards(e);
        if (all.stream().anyMatch(v -> !v.a().isComplete())) {
            return false;
        }
        Collection<Reward> rewards = getMorningRewards(e);
        if (rewards.stream().anyMatch(Reward::addsQuestsWhenApplied)) {
            return false;
        }
        return true;
    }

    protected abstract ImmutableList<Pair<Completable, Reward>> getAllQuestsWithRewards(TICK_DATA e);

    protected abstract ImmutableList<Reward> getMorningRewards(TICK_DATA e);

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
        if (Config.TICK_SAMPLING_RATE.get() > 0) {
            long end = System.currentTimeMillis();
            times.add((int) (end - start));

            if (times.size() > Config.TICK_SAMPLING_RATE.get()) {
                double tl = times.stream().mapToInt(Integer::intValue).average().orElse(0);
                QT.PROFILE_LOGGER.debug("Average tick length: {}", tl);
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
