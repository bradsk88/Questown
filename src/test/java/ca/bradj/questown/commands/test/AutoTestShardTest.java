package ca.bradj.questown.commands.test;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

class AutoTestShardTest {

    private static final List<String> JOBS = List.of("a", "b", "c", "d", "e");
    private static final List<String> CHICKEN = List.of("x", "y");

    @Test
    void everyScenarioRunsInExactlyOneShard() {
        List<String> seen = new ArrayList<>();
        for (int i = 1; i <= 3; i++) {
            AutoTestShard s = new AutoTestShard(i, 3);
            seen.addAll(s.select(JOBS, 0));
            seen.addAll(s.select(CHICKEN, JOBS.size()));
        }
        Assertions.assertEquals(7, seen.size());
        Assertions.assertTrue(seen.containsAll(JOBS) && seen.containsAll(CHICKEN));
    }

    @Test
    void shardsInterleaveAcrossBothLists() {
        AutoTestShard first = new AutoTestShard(1, 3);
        Assertions.assertEquals(List.of("a", "d"), first.select(JOBS, 0));
        Assertions.assertEquals(List.of("y"), first.select(CHICKEN, JOBS.size()));
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

    @Test
    void aSoloScenarioGetsAShardToItself() {
        List<String> names = List.of("a", "b", "big", "c", "d", "e");
        Set<String> solo = Set.of("big");
        Assertions.assertEquals(Set.of("big"), new AutoTestShard(1, 3).selectNames(names, solo));
        Set<String> two = new AutoTestShard(2, 3).selectNames(names, solo);
        Set<String> three = new AutoTestShard(3, 3).selectNames(names, solo);
        Assertions.assertEquals(Set.of("a", "c", "e"), two);
        Assertions.assertEquals(Set.of("b", "d"), three);
    }

    @Test
    void moreSolosThanShardsFallsBackToRoundRobin() {
        List<String> names = List.of("x", "y", "z");
        Set<String> all = new java.util.HashSet<>();
        all.addAll(new AutoTestShard(1, 2).selectNames(names, Set.of("x", "y", "z")));
        all.addAll(new AutoTestShard(2, 2).selectNames(names, Set.of("x", "y", "z")));
        Assertions.assertEquals(Set.of("x", "y", "z"), all);
    }
}
