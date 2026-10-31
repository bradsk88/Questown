package ca.bradj.questown.commands.test;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * One server's share of the autotest suite when several run in parallel
 * ({@code -Dquestown.autotest.shard=<i>/<n>}, 1-based; tools/autotest-parallel.sh --shard). The
 * filtered scenario list is numbered as one sequence (jobs, then chicken arc) and shard {@code i}
 * keeps every {@code n}th scenario starting at {@code i - 1}, so long scenarios spread out and
 * every scenario runs in exactly one shard.
 */
public record AutoTestShard(int index, int count) {

    public static final AutoTestShard ALL = new AutoTestShard(1, 1);

    /** Parses {@code "i/n"}; null or blank means the whole suite. */
    public static AutoTestShard parse(@Nullable String value) {
        if (value == null || value.isBlank()) {
            return ALL;
        }
        String[] parts = value.trim().split("/");
        if (parts.length != 2) {
            throw new IllegalArgumentException("shard must be <i>/<n>, got " + value);
        }
        int i = Integer.parseInt(parts[0].trim());
        int n = Integer.parseInt(parts[1].trim());
        if (n < 1 || i < 1 || i > n) {
            throw new IllegalArgumentException("shard must have 1 <= i <= n, got " + value);
        }
        return new AutoTestShard(i, n);
    }

    /**
     * This shard's part of {@code list}, whose first element is number {@code offset} in the
     * combined sequence.
     */
    public <T> List<T> select(List<T> list, int offset) {
        List<T> mine = new ArrayList<>();
        for (int k = 0; k < list.size(); k++) {
            if ((offset + k) % count == index - 1) {
                mine.add(list.get(k));
            }
        }
        return mine;
    }

    /**
     * This shard's scenario names, with each {@code solo} scenario on a shard of its own (shards
     * 1..S) and the rest round-robin over the remaining shards. A scenario far longer than the
     * rest sets the wall time of whichever shard holds it, so it should hold nothing else
     * (perf/town_large: 218 s of a ~670 s suite, 2026-10-20). With as many solos as shards, it is
     * plain round-robin.
     */
    public Set<String> selectNames(List<String> names, Set<String> solo) {
        List<String> solos = names.stream().filter(solo::contains).toList();
        if (solos.size() >= count) {
            return new LinkedHashSet<>(select(names, 0));
        }
        Set<String> mine = new LinkedHashSet<>();
        if (index <= solos.size()) {
            mine.add(solos.get(index - 1));
            return mine;
        }
        List<String> rest = names.stream().filter(n -> !solo.contains(n)).toList();
        int restShards = count - solos.size();
        for (int k = 0; k < rest.size(); k++) {
            if (k % restShards == index - 1 - solos.size()) {
                mine.add(rest.get(k));
            }
        }
        return mine;
    }

    public boolean isWholeSuite() {
        return count == 1;
    }
}
