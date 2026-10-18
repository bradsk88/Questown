package ca.bradj.questown.commands.test;

import ca.bradj.questown.core.init.TagsInit;
import ca.bradj.questown.jobs.JobID;
import ca.bradj.questown.jobs.ServerJobsRegistry;
import ca.bradj.questown.mobs.visitor.VisitorMobEntity;
import ca.bradj.questown.town.entity.TownFlagBlockEntity;
import ca.bradj.questown.town.quests.MCQuest;
import ca.bradj.questown.town.quests.Quest;
import ca.bradj.questown.town.special.SpecialQuests;

import java.util.AbstractMap;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * The day-1 onboarding beats the 2026-10-15/17 playtests kept re-checking by hand, as named
 * checks over the live town state (village/day1). An observer only: the driver
 * ({@link VillageGrowthExecutor} in day1 mode) plays; this records PASS/FAIL as beats happen.
 */
final class Day1BeatChecks {

    /** Evening begins at Signals.PRODUCTIVE_DAY_END_TICK; give the flag tick a moment. */
    private static final long EVENING_START = 11500;
    private static final long EVENING_GRACE = 200;
    private static final Set<String> NOT_A_STARTING_COOK_JOB = Set.of("stock_ingredients", "stock_fuel");

    private final UUID player;
    private final BiConsumer<String, Boolean> check;
    private final Consumer<String> msg;

    private boolean toastEvaluated;
    private boolean toastBeforeDusk;
    private long lastRoster;
    // In-game day of the last arrival and how many arrived that day: onboarding brings at most
    // one villager per morning (2026-10-18 playtest: the basics and hunter-gatherer batches
    // both queued one, and they landed together; a roster step check sees 1->2->3 as two
    // separate single arrivals).
    private long lastArrivalDay = -1;
    private int arrivalsThatDay;
    private boolean workRequestQuestSeen;
    private boolean rawFoodSeenComplete;
    private boolean cookSeen;

    Day1BeatChecks(UUID player, BiConsumer<String, Boolean> check, Consumer<String> msg) {
        this.player = player;
        this.check = check;
        this.msg = msg;
    }

    private boolean arrivalsPaused;

    boolean cookBeatReached() {
        return cookSeen;
    }

    /** During a reload the roster drops and re-registers: not arrivals. */
    void pauseArrivals() {
        arrivalsPaused = true;
    }

    void resumeArrivals(long rosterNow) {
        arrivalsPaused = false;
        lastRoster = rosterNow;
    }

    void tick(TownFlagBlockEntity flag, long dayTime) {
        checkToast(flag, dayTime % 24000L);
        checkArrivals(flag, dayTime / 24000L);
        checkWorkRequestQuest(flag);
        checkRawFood(flag);
        checkCook(flag);
    }

    private void checkToast(TownFlagBlockEntity flag, long timeOfDay) {
        if (toastEvaluated || !campfireComplete(flag)) {
            return;
        }
        boolean shown = flag.hasShownCampReadyToast(player);
        if (timeOfDay < EVENING_START) {
            toastBeforeDusk |= shown;
            return;
        }
        if (timeOfDay < EVENING_START + EVENING_GRACE) {
            return;
        }
        toastEvaluated = true;
        check.accept("toast-not-before-dusk", !toastBeforeDusk);
        check.accept("toast-at-first-dusk", shown);
    }

    private void checkArrivals(TownFlagBlockEntity flag, long day) {
        if (arrivalsPaused) {
            return;
        }
        long roster = flag.getVillagerHandle().size();
        if (roster <= lastRoster) {
            lastRoster = roster;
            return;
        }
        boolean rawFoodDone = rawFoodComplete(flag);
        msg.accept("arrival: roster " + lastRoster + " -> " + roster + " (raw food complete: " + rawFoodDone + ")");
        if (lastRoster == 0 && roster > 1) {
            check.accept("first-morning-brings-one-villager", false);
        }
        if (lastRoster <= 1 && roster >= 2) {
            check.accept("villager-2-only-after-raw-food", rawFoodDone);
        }
        checkOneArrivalPerDay(day, roster - lastRoster);
        lastRoster = roster;
    }

    private void checkOneArrivalPerDay(long day, long arrived) {
        if (day != lastArrivalDay) {
            lastArrivalDay = day;
            arrivalsThatDay = 0;
        }
        arrivalsThatDay += (int) arrived;
        check.accept("one-arrival-per-day[day " + day + "]", arrivalsThatDay <= 1);
    }

    private void checkWorkRequestQuest(TownFlagBlockEntity flag) {
        if (workRequestQuestSeen) {
            return;
        }
        boolean posted = flag.getQuestHandle().getAllQuestsWithRewards().stream()
                .map(AbstractMap.SimpleEntry::getKey)
                .anyMatch(q -> q.getType() == Quest.QuestType.FIRST_WORK_REQUEST);
        if (!posted) {
            return;
        }
        workRequestQuestSeen = true;
        check.accept("work-request-quest-only-with-a-villager", flag.getVillagerHandle().size() >= 1);
    }

    private void checkRawFood(TownFlagBlockEntity flag) {
        if (rawFoodSeenComplete || !rawFoodComplete(flag)) {
            return;
        }
        rawFoodSeenComplete = true;
        boolean hunterInTown = villagers(flag).stream().anyMatch(v -> "hunter".equals(v.getJobId().rootId()));
        check.accept("raw-food-completed-by-a-hunter", hunterInTown);
    }

    private void checkCook(TownFlagBlockEntity flag) {
        if (cookSeen) {
            return;
        }
        for (VisitorMobEntity v : villagers(flag)) {
            if (!"cook".equals(v.getJobId().rootId())) {
                continue;
            }
            cookSeen = true;
            msg.accept("cook appeared on " + v.getJobId());
            check.accept("cook-starts-on-a-starting-job[" + v.getJobId().jobId() + "]",
                    !NOT_A_STARTING_COOK_JOB.contains(v.getJobId().jobId()));
            // A new root's default work (cook: extract + fuel + the starter recipe) must be
            // unlocked however the root change happened; the BOP path used to unlock only the
            // job it switched to (2026-10-18 playtest: no fuel / cook_mutton).
            for (JobID starter : ServerJobsRegistry.getDefaultWork(v.getJobId())) {
                check.accept("cook-starter-job-unlocked[" + starter.jobId() + "]",
                        flag.getVillagerHandle().isUnlocked(v.getUUID(), starter));
            }
            return;
        }
    }

    private static java.util.List<VisitorMobEntity> villagers(TownFlagBlockEntity flag) {
        return flag.getVillagerHandle().entities().stream()
                .filter(VisitorMobEntity.class::isInstance)
                .map(VisitorMobEntity.class::cast)
                .toList();
    }

    private static boolean campfireComplete(TownFlagBlockEntity flag) {
        return flag.getQuestHandle().getAllQuestsWithRewards().stream()
                .map(AbstractMap.SimpleEntry::getKey)
                .anyMatch(q -> SpecialQuests.CAMPFIRE.equals(q.getWantedId()) && q.isComplete());
    }

    static boolean rawFoodComplete(TownFlagBlockEntity flag) {
        return flag.getQuestHandle().getAllQuestsWithRewards().stream()
                .map(AbstractMap.SimpleEntry::getKey)
                .anyMatch(Day1BeatChecks::isCompletedRawFood);
    }

    private static boolean isCompletedRawFood(MCQuest q) {
        return q.getType() == Quest.QuestType.LOOT
                && TagsInit.Items.VILLAGER_SIMPLE_FURNACE_FOOD_RAW.location().equals(q.getWantedId())
                && q.isComplete();
    }
}
