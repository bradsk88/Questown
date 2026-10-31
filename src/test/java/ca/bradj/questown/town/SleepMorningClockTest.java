package ca.bradj.questown.town;

import ca.bradj.questown.gui.ItemEconomicsData;
import ca.bradj.questown.jobs.Signals;
import com.google.common.collect.ImmutableList;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * r16 #6: a slept night's morning passed the new *daytime* into the morning pass, while the state
 * it writes (SLEEPLESS expiry, last-downtime) is read on *game time*. Sleep skips advance daytime
 * but not game time, so every slept night stretched those timers. The slept morning must run on
 * the same clock as the ticker's own morning detection: {@code getTick} (game time).
 */
class SleepMorningClockTest {

    private static final long GAME_TIME = 50_460L;
    private static final long DAY_TIME_AFTER_SLEEP = 72_000L;

    @Test
    void sleptMorning_runsOnGameTime_notTheNewDaytime() {
        RecordingTicker ticker = new RecordingTicker();

        ticker.runSleptMorning(new Object());

        Assertions.assertEquals(GAME_TIME, ticker.morningTick);
    }

    @Test
    void sleptMorning_marksTheMorningConsumed() {
        RecordingTicker ticker = new RecordingTicker();

        ticker.runSleptMorning(new Object());
        ticker.runSleptMorning(new Object());

        Assertions.assertEquals(2, ticker.mornings, "each slept wake runs one morning pass");
    }

    private static class RecordingTicker extends AbstractTownFlagTicker<Object, Object> {
        long morningTick = -1;
        int mornings = 0;

        @Override
        protected void onMorning(Object data, Long pop) {
            morningTick = pop;
            mornings++;
        }

        @Override
        protected long getTick(Object data) {
            return GAME_TIME;
        }

        @Override
        protected Signals.DayTime getDayTime(Object data) {
            return new Signals.DayTime(DAY_TIME_AFTER_SLEEP);
        }

        @Override
        protected void triggerUnmetNeedsAdvancement(Object data) {
        }

        @Override
        protected ImmutableList<ItemEconomicsData> getTownLevelItemEconomics(Object data) {
            return ImmutableList.of();
        }

        @Override
        protected boolean applyNewEconomicsData(Object data) {
            return false;
        }

        @Override
        protected void tickVillagers(Object data, Signals signals) {
        }

        @Override
        protected void triggerFirstNightAdvancement(Object data) {
        }

        @Override
        protected void tickEvening(Object data) {
        }

        @Override
        protected void tickPOIs(Object data) {
        }

        @Override
        protected void tickASAPRewards(Object data) {
        }

        @Override
        protected void updateWorkStatuses(Object data) {
        }

        @Override
        protected boolean isFlagTick(Object data) {
            return false;
        }

        @Override
        protected void tickRoomsHandle(Object data) {
        }

        @Override
        protected void tickPossibleWorkHandle(Object data) {
        }

        @Override
        protected void tickHealingHandle(Object data) {
        }

        @Override
        protected void tickBiomesHandle(Object data) {
        }

        @Override
        protected void tickQuests(Object data) {
        }

        @Override
        protected void tickWorkHandle(Object data) {
        }

        @Override
        protected void handleNewStoredData(Object data) {
        }

        @Override
        protected boolean updateStoredData(Object data) {
            return false;
        }

        @Override
        protected void notifySubBlocksOfTick(Object data) {
        }

        @Override
        protected void storeInactiveState(Object data) {
        }

        @Override
        protected boolean allPlayersLeftArea(Object data, boolean playersNearbyOnPrevTick) {
            return false;
        }

        @Override
        protected ImmutableList<Object> getVillagers(Object data) {
            return ImmutableList.of();
        }

        @Override
        protected boolean runAnyInitializers(Object data) {
            return false;
        }
    }
}
