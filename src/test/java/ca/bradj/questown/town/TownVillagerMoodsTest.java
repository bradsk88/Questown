package ca.bradj.questown.town;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Harness-gated notes for {@link TownVillagerMoods}. Both paths that would
 * unit-test the SLEEPLESS effect touch a NeoForge config value that is not
 * loaded outside a mod run: the constructor reads {@code Config.MOOD_TICK_INTERVAL}
 * and {@code compute} reads {@code Config.NEUTRAL_MOOD} — either throws
 * "Cannot get config value before config is loaded" in a plain JVM test
 * (reproduced 2026-10-09). The SLEEPLESS magnitude (−15: neutral 75 → 60 →
 * work speed 7 → 6) is therefore verified in the held self-sufficiency run
 * instead: the dawn "SLEEPLESS applied" log line plus the
 * {@code sleep bedless=K sleepless=M} daily-report line. See
 * {@code docs/plans/2026-10-09-003-feat-sleepless-no-bed-debuff-plan.md}.
 */
class TownVillagerMoodsTest {

    @Test
    void todo_compute_sleeplessLowersMoodByFifteen_isNotReachableInAUnitTest() {
        Assertions.fail("not unit-testable: TownVillagerMoods.compute reads Config.NEUTRAL_MOOD "
                + "(NeoForge config, unloaded in a plain JVM). Would assert: "
                + "compute({SLEEPLESS}) == 60 (neutral 75 - 15) and "
                + "compute({SLEEPLESS, ATE_RAW_FOOD}) == 55.");
    }

    @Test
    void todo_hasEffect_sleepless_isNotReachableInAUnitTest() {
        Assertions.fail("not unit-testable: the TownVillagerMoods ctor reads "
                + "Config.MOOD_TICK_INTERVAL (same harness gate). Would assert: "
                + "tryApplyEffect(SLEEPLESS) then hasEffect(v, SLEEPLESS) == true, "
                + "hasEffect(v, COMFORTABLE_EATING) == false.");
    }
}
