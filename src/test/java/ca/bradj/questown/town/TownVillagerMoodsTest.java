package ca.bradj.questown.town;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

/**
 * Unit tests for {@link TownVillagerMoods} mood math and effect tracking, made
 * possible by the config-split: {@code compute(int neutral, effects)} takes the
 * neutral value as a parameter (no {@code Config.NEUTRAL_MOOD} read) and the
 * package-private {@code TownVillagerMoods(long)} ctor takes a fixed tick
 * interval (no {@code Config.MOOD_TICK_INTERVAL} read). {@code MoodEffects} and
 * {@code Effect} are registry-free ({@code ResourceLocation}), and
 * {@link TownVillagerMoods#staticInitialize()} is pure, so all of this runs in a
 * plain JVM test.
 *
 * <p>(Replaces two former {@code todo_...} markers that could not run because the
 * old ctor and {@code compute} each read a NeoForge config value that is unloaded
 * outside a mod run.)
 */
class TownVillagerMoodsTest {

    @BeforeAll
    static void initBuffs() {
        TownVillagerMoods.staticInitialize();
    }

    @Test
    void compute_sleeplessLowersMoodByFifteen() {
        // neutral 75 - SLEEPLESS(15) = 60
        Assertions.assertEquals(60, TownVillagerMoods.compute(75, List.of(
                new Effect(MoodEffects.SLEEPLESS, 10_000L))));
        // 75 - SLEEPLESS(15) - ATE_RAW_FOOD(10) = 50
        Assertions.assertEquals(50, TownVillagerMoods.compute(75, List.of(
                new Effect(MoodEffects.SLEEPLESS, 10_000L),
                new Effect(MoodEffects.ATE_RAW_FOOD, 10_000L))));
    }

    @Test
    void compute_clampsToZeroToHundred() {
        // 100 + COMFORTABLE_EATING(5) = 105 -> clamped to 100
        Assertions.assertEquals(100, TownVillagerMoods.compute(100, List.of(
                new Effect(MoodEffects.COMFORTABLE_EATING, 10_000L))));
        // 0 - ATE_RAW_FOOD(10) = -10 -> clamped to 0
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
