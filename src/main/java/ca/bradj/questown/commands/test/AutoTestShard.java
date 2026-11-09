package ca.bradj.questown.commands.test;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * One server's share of the autotest suite when several run in parallel
 * ({@code -Dquestown.autotest.shard=<i>/<n>}, 1-based; tools/autotest-parallel.sh --shard). The
 * filtered scenario list is numbered as one sequence and shard {@code i}
 * keeps every {@code n}th scenario starting at {@code i - 1}; {@link #selectNames} balances by
 * recorded durations instead. Either way every scenario runs in exactly one shard.
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
     * This shard's scenario names, in suite order. Longest first, each scenario goes to the shard
     * with the least time so far (ties to the lowest shard), using {@code durations} in ms from an
     * earlier run; a scenario with no recorded duration counts as the median. Every shard computes
     * the same split, and with no durations it is plain round-robin.
     */
    public Set<String> selectNames(List<String> names, Map<String, Long> durations) {
        long unknown = median(names, durations);
        long[] cost = new long[names.size()];
        List<Integer> longestFirst = new ArrayList<>();
        for (int k = 0; k < names.size(); k++) {
            cost[k] = durations.getOrDefault(names.get(k), unknown);
            longestFirst.add(k);
        }
        longestFirst.sort(Comparator.comparingLong(k -> -cost[k]));
        long[] load = new long[count];
        boolean[] mine = new boolean[names.size()];
        for (int k : longestFirst) {
            int least = 0;
            for (int s = 1; s < count; s++) {
                if (load[s] < load[least]) {
                    least = s;
                }
            }
            load[least] += cost[k];
            mine[k] = least == index - 1;
        }
        Set<String> out = new LinkedHashSet<>();
        for (int k = 0; k < names.size(); k++) {
            if (mine[k]) {
                out.add(names.get(k));
            }
        }
        return out;
    }

    private static long median(List<String> names, Map<String, Long> durations) {
        List<Long> known = names.stream().filter(durations::containsKey).map(durations::get).sorted().toList();
        return known.isEmpty() ? 1 : known.get(known.size() / 2);
    }

    /**
     * Reads the durations file tools/autotest-parallel.sh keeps ({@code <ms>\t<name>} per line).
     * Lines that don't parse are skipped.
     */
    public static Map<String, Long> parseDurations(List<String> lines) {
        Map<String, Long> out = new HashMap<>();
        for (String line : lines) {
            String[] parts = line.split("\t", 2);
            if (parts.length != 2) {
                continue;
            }
            try {
                out.put(parts[1], Long.parseLong(parts[0].trim()));
            } catch (NumberFormatException ignored) {
                // A hand-edited or truncated line: balance without it.
            }
        }
        return out;
    }

    public boolean isWholeSuite() {
        return count == 1;
    }
}
