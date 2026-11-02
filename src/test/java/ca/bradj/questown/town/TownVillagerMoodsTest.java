package ca.bradj.questown.town;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

/** {@link TownVillagerMoods}: mood from a neutral value and effects, and effect tracking. */
class TownVillagerMoodsTest {

    @BeforeAll
    static void initBuffs() {
        TownVillagerMoods.staticInitialize();
    }

    @Test
    void compute_sleeplessLowersMoodByFifteen() {
        Assertions.assertEquals(60, TownVillagerMoods.compute(75, List.of(
                new Effect(MoodEffects.SLEEPLESS, 10_000L))));
        Assertions.assertEquals(50, TownVillagerMoods.compute(75, List.of(
                new Effect(MoodEffects.SLEEPLESS, 10_000L),
                new Effect(MoodEffects.ATE_RAW_FOOD, 10_000L))));
    }

    @Test
    void compute_clampsToZeroToHundred() {
        // Capped at 100.
        Assertions.assertEquals(100, TownVillagerMoods.compute(100, List.of(
                new Effect(MoodEffects.COMFORTABLE_EATING, 10_000L))));
        // Floored at 0.
        Assertions.assertEquals(0, TownVillagerMoods.compute(0, List.of(
                new Effect(MoodEffects.ATE_RAW_FOOD, 10_000L))));
    }

    @Test
    void hasEffect_tracksAppliedEffect() {
        TownVillagerMoods moods = new TownVillagerMoods(10L);
        UUID villager = UUID.randomUUID();
        moods.tryApplyEffect(MoodEffects.SLEEPLESS, 10_000L, villager);
        Assertions.assertTrue(moods.hasEffect(villager, MoodEffects.SLEEPLESS));
        Assertions.assertFalse(moods.hasEffect(villager, MoodEffects.COMFORTABLE_EATING));
    }
}
