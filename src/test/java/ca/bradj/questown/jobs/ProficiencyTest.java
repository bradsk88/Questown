package ca.bradj.questown.jobs;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

class ProficiencyTest {

    private static final List<String> POOL = List.of("farming", "smithing", "cooking", "mining", "fishing");

    @Test
    void seedIsDeterministicForSameUuid() {
        UUID u = UUID.fromString("00000000-0000-0000-0000-000000000001");
        Map<String, Float> a = Proficiency.seed(u, POOL, 3);
        Map<String, Float> b = Proficiency.seed(u, POOL, 3);
        Assertions.assertEquals(a, b);
    }

    @Test
    void seedDrawsRequestedDistinctCount() {
        UUID u = UUID.randomUUID();
        Map<String, Float> seed = Proficiency.seed(u, POOL, 3);
        Assertions.assertEquals(3, seed.size());
        POOL.forEach(id -> {
            if (seed.containsKey(id)) {
                Assertions.assertTrue(seed.get(id) >= 0f && seed.get(id) < 1f);
            }
        });
    }

    @Test
    void seedIsInsensitiveToDeclaredIdOrder() {
        UUID u = UUID.fromString("00000000-0000-0000-0000-000000000002");
        Map<String, Float> a = Proficiency.seed(u, List.of("a", "b", "c", "d"), 2);
        Map<String, Float> b = Proficiency.seed(u, List.of("d", "c", "b", "a"), 2);
        Assertions.assertEquals(a, b);
    }

    @Test
    void seedCapsAtPoolSize() {
        UUID u = UUID.randomUUID();
        Map<String, Float> seed = Proficiency.seed(u, List.of("only"), 3);
        Assertions.assertEquals(1, seed.size());
    }

    @Test
    void seedEmptyWhenNoIdsOrZeroCount() {
        UUID u = UUID.randomUUID();
        Assertions.assertTrue(Proficiency.seed(u, POOL, 0).isEmpty());
        Assertions.assertTrue(Proficiency.seed(u, List.of(), 3).isEmpty());
    }

    @Test
    void levelUpGainsWorkedAndDecaysOthers() {
        Map<String, Float> before = Map.of("farming", 0.5f, "smithing", 0.5f);
        Map<String, Float> after = Proficiency.levelUp(before, "farming", 0.001f, 0.0001f, 100);
        Assertions.assertEquals(0.6f, after.get("farming"), EPS);   // +0.001*100
        Assertions.assertEquals(0.49f, after.get("smithing"), EPS); // -0.0001*100
    }

    @Test
    void levelUpCreatesWorkedIdAtZeroIfAbsent() {
        Map<String, Float> before = Map.of("smithing", 0.5f);
        Map<String, Float> after = Proficiency.levelUp(before, "farming", 0.001f, 0.0001f, 100);
        Assertions.assertEquals(0.1f, after.get("farming"), EPS);
        Assertions.assertEquals(0.49f, after.get("smithing"), EPS);
    }

    @Test
    void levelUpClampsGainAndDecay() {
        Map<String, Float> before = Map.of("farming", 0.99f, "smithing", 0.01f);
        Map<String, Float> after = Proficiency.levelUp(before, "farming", 0.001f, 0.0001f, 100000);
        Assertions.assertEquals(1f, after.get("farming"), EPS);
        Assertions.assertEquals(0f, after.get("smithing"), EPS);
    }

    @Test
    void repeatedLevelUpDecaysNeglectedToZero() {
        // The "decay-to-zero-over-neglect" invariant: working only farming floors smithing at 0.
        Map<String, Float> m = new java.util.HashMap<>(Map.of("farming", 0.2f, "smithing", 0.5f));
        for (int i = 0; i < 200; i++) {
            m = Proficiency.levelUp(m, "farming", 0.001f, 0.0001f, 100);
        }
        Assertions.assertEquals(0f, m.get("smithing"), EPS);
        Assertions.assertEquals(1f, m.get("farming"), EPS);
    }

    private static final float MIN = 0.5f;
    private static final float MAX = 2.0f;
    private static final float EPS = 1e-6f;

    @Test
    void multiplierAtLevelZeroIsMin() {
        Assertions.assertEquals(MIN, Proficiency.multiplierFor(0f, MIN, MAX), EPS);
    }

    @Test
    void multiplierAtLevelOneIsMax() {
        Assertions.assertEquals(MAX, Proficiency.multiplierFor(1f, MIN, MAX), EPS);
    }

    @Test
    void multiplierIsLinearAtMidpoint() {
        Assertions.assertEquals(1.25f, Proficiency.multiplierFor(0.5f, MIN, MAX), EPS);
    }

    @Test
    void breakEvenLevelYieldsOneTimes() {
        // With 0.5..2.0, level 1/3 is the break-even (1x) point.
        float breakEven = 1f / 3f;
        Assertions.assertEquals(1.0f, Proficiency.multiplierFor(breakEven, MIN, MAX), 1e-4f);
    }

    @Test
    void multiplierClampsLevelBelowZero() {
        Assertions.assertEquals(MIN, Proficiency.multiplierFor(-5f, MIN, MAX), EPS);
    }

    @Test
    void multiplierClampsLevelAboveOne() {
        Assertions.assertEquals(MAX, Proficiency.multiplierFor(5f, MIN, MAX), EPS);
    }

    @Test
    void gainAddsProportionalToDuration() {
        Assertions.assertEquals(0.1f, Proficiency.applyGain(0f, 0.001f, 100), EPS);
    }

    @Test
    void gainClampsAtOne() {
        Assertions.assertEquals(1f, Proficiency.applyGain(0.99f, 0.001f, 1000), EPS);
    }

    @Test
    void decaySubtractsProportionalToDuration() {
        Assertions.assertEquals(0.4f, Proficiency.applyDecay(0.5f, 0.0001f, 1000), EPS);
    }

    @Test
    void decayClampsAtZero() {
        Assertions.assertEquals(0f, Proficiency.applyDecay(0.01f, 0.0001f, 1000), EPS);
    }
}
