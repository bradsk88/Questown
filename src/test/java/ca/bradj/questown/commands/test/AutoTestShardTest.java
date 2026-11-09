package ca.bradj.questown.commands.test;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

class AutoTestShardTest {

    private static final List<String> JOBS = List.of("a", "b", "c", "d", "e");
    private static final List<String> OTHER = List.of("x", "y");

    @Test
    void everyScenarioRunsInExactlyOneShard() {
        List<String> seen = new ArrayList<>();
        for (int i = 1; i <= 3; i++) {
            AutoTestShard s = new AutoTestShard(i, 3);
            seen.addAll(s.select(JOBS, 0));
            seen.addAll(s.select(OTHER, JOBS.size()));
        }
        Assertions.assertEquals(7, seen.size());
        Assertions.assertTrue(seen.containsAll(JOBS) && seen.containsAll(OTHER));
    }

    @Test
    void shardsInterleaveAcrossBothLists() {
        AutoTestShard first = new AutoTestShard(1, 3);
        Assertions.assertEquals(List.of("a", "d"), first.select(JOBS, 0));
        Assertions.assertEquals(List.of("y"), first.select(OTHER, JOBS.size()));
    }

    @Test
    void blankMeansTheWholeSuite() {
        Assertions.assertTrue(AutoTestShard.parse(null).isWholeSuite());
        Assertions.assertTrue(AutoTestShard.parse(" ").isWholeSuite());
        Assertions.assertEquals(JOBS, AutoTestShard.parse(null).select(JOBS, 0));
    }

    @Test
    void parsesAndRejectsOutOfRange() {
        Assertions.assertEquals(new AutoTestShard(2, 4), AutoTestShard.parse("2/4"));
        Assertions.assertThrows(IllegalArgumentException.class, () -> AutoTestShard.parse("5/4"));
        Assertions.assertThrows(IllegalArgumentException.class, () -> AutoTestShard.parse("0/4"));
        Assertions.assertThrows(IllegalArgumentException.class, () -> AutoTestShard.parse("2"));
    }

    private static List<Set<String>> split(List<String> names, Map<String, Long> durations, int count) {
        List<Set<String>> shards = new ArrayList<>();
        for (int i = 1; i <= count; i++) {
            shards.add(new AutoTestShard(i, count).selectNames(names, durations));
        }
        return shards;
    }

    @Test
    void selectNames_shouldBeRoundRobin_whenNoDurationsAreKnown() {
        List<Set<String>> shards = split(JOBS, Map.of(), 3);
        Assertions.assertEquals(List.of(Set.of("a", "d"), Set.of("b", "e"), Set.of("c")), shards);
    }

    @Test
    void selectNames_shouldGiveAScenarioAShardToItself_whenItIsLongerThanAnEvenShare() {
        List<String> names = List.of("a", "b", "big", "c", "d", "e");
        Map<String, Long> durations = Map.of(
                "a", 10L, "b", 10L, "big", 1000L, "c", 10L, "d", 10L, "e", 10L
        );
        List<Set<String>> shards = split(names, durations, 3);
        Assertions.assertEquals(Set.of("big"), shards.get(0));
        Assertions.assertEquals(6, shards.stream().mapToInt(Set::size).sum(), "a scenario was lost or doubled");
    }

    @Test
    void selectNames_shouldSpreadTheLongestScenarios_whenRoundRobinWouldStackThem() {
        // Round-robin on 2 shards puts a, c and e (the long ones) together: 180 vs 30. Two of the three
        // must share a shard, so 120 is the best split.
        List<String> names = List.of("a", "b", "c", "d", "e", "f");
        Map<String, Long> durations = Map.of(
                "a", 60L, "b", 10L, "c", 60L, "d", 10L, "e", 60L, "f", 10L
        );
        List<Set<String>> shards = split(names, durations, 2);
        long first = shards.get(0).stream().mapToLong(durations::get).sum();
        long second = shards.get(1).stream().mapToLong(durations::get).sum();
        Assertions.assertEquals(120L, Math.max(first, second), "shards were not balanced: " + shards);
    }

    @Test
    void selectNames_shouldCountAnUnknownScenarioAsTheMedian() {
        // "new" has no duration. As the median (20) it is placed before z and joins y; counted as
        // nothing it would be placed last and join x.
        List<String> names = List.of("x", "y", "z", "new");
        Map<String, Long> durations = Map.of("x", 30L, "y", 20L, "z", 10L);
        List<Set<String>> shards = split(names, durations, 2);
        Assertions.assertTrue(shards.get(1).contains("new"), "unknown scenario went to the busier shard: " + shards);
    }

    @Test
    void selectNames_shouldKeepSuiteOrder() {
        Map<String, Long> durations = Map.of("a", 1L, "b", 1L, "c", 50L, "d", 1L, "e", 1L);
        Set<String> shard = new AutoTestShard(2, 2).selectNames(JOBS, durations);
        Assertions.assertEquals(List.of("a", "b", "d", "e"), new ArrayList<>(shard));
    }

    @Test
    void parseDurations_shouldSkipLinesThatDoNotParse() {
        Map<String, Long> durations = AutoTestShard.parseDurations(List.of(
                "1200\tcook/fuel [no_coal]",
                "garbage",
                "12x\tbroken",
                "80000\tperf/town_large"
        ));
        Assertions.assertEquals(Map.of("cook/fuel [no_coal]", 1200L, "perf/town_large", 80000L), durations);
    }
}
