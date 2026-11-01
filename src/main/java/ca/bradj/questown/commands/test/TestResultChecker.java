package ca.bradj.questown.commands.test;

import ca.bradj.questown.integration.minecraft.MCHeldItem;
import ca.bradj.questown.integration.minecraft.MCTownItem;
import ca.bradj.questown.integration.minecraft.MCTownState;
import ca.bradj.questown.jobs.leaver.ContainerTarget;
import ca.bradj.questown.town.TownState;
import net.minecraft.core.BlockPos;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class TestResultChecker {

    public record Result(boolean passed, String summary, List<String> details, Map<String, Integer> deltas) {}

    public static Map<String, Integer> snapshotVillagerHeldCounts(MCTownState state) {
        Map<String, Integer> counts = new HashMap<>();
        for (TownState.VillagerData<MCHeldItem> villager : state.villagers) {
            for (MCHeldItem item : villager.journal.items()) {
                if (item.isEmpty()) {
                    continue;
                }
                counts.merge(item.getShortName(), 1, Integer::sum);
            }
        }
        return counts;
    }

    public static Map<String, Integer> snapshotItemCounts(MCTownState state) {
        Map<String, Integer> counts = new HashMap<>();

        for (TownState.VillagerData<MCHeldItem> villager : state.villagers) {
            for (MCHeldItem item : villager.journal.items()) {
                if (item.isEmpty()) {
                    continue;
                }
                counts.merge(item.getShortName(), 1, Integer::sum);
            }
        }

        for (ContainerTarget<?, MCTownItem> container : state.containers) {
            for (MCTownItem item : container.getItems()) {
                if (item.isEmpty()) {
                    continue;
                }
                String name = itemRegistryName(item);
                counts.merge(name, item.quantity(), Integer::sum);
            }
        }

        return counts;
    }

    /**
     * Per-{@link BlockPos} item counts, one map per container position. Unlike
     * {@link #snapshotItemCounts} (which flattens every villager + container into one town-wide
     * total), this preserves <em>where</em> each item sits, so a relocation (source loses N,
     * target gains N) is observable even though the town-wide delta is ~0.
     */
    public static Map<BlockPos, Map<String, Integer>> snapshotContainerContents(MCTownState state) {
        Map<BlockPos, Map<String, Integer>> byPos = new HashMap<>();
        for (ContainerTarget<?, MCTownItem> container : state.containers) {
            Map<String, Integer> counts = byPos.computeIfAbsent(container.getBlockPos(), p -> new HashMap<>());
            for (MCTownItem item : container.getItems()) {
                if (item.isEmpty()) {
                    continue;
                }
                counts.merge(itemRegistryName(item), item.quantity(), Integer::sum);
            }
        }
        return byPos;
    }

    /**
     * Checks each {@link TestExpectation.ExpectedContainerContent} against the per-position
     * before/after snapshots from {@link #snapshotContainerContents}. A chest absent from both
     * snapshots fails with a clear "container not found" detail.
     */
    public static Result checkContainerContents(
            Map<BlockPos, Map<String, Integer>> before,
            Map<BlockPos, Map<String, Integer>> after,
            TestExpectation expectation
    ) {
        List<String> details = new ArrayList<>();
        boolean allPassed = true;

        for (TestExpectation.ExpectedContainerContent c : expectation.containerContents()) {
            if (!before.containsKey(c.chest()) && !after.containsKey(c.chest())) {
                details.add(String.format(
                        "[FAIL] %s @ %s: container not found in snapshot",
                        c.item(), c.chest().toShortString()
                ));
                allPassed = false;
                continue;
            }

            int beforeCount = before.getOrDefault(c.chest(), Map.of()).getOrDefault(c.item(), 0);
            int afterCount = after.getOrDefault(c.chest(), Map.of()).getOrDefault(c.item(), 0);
            int delta = afterCount - beforeCount;
            boolean passed = (c.minDelta() == null || delta >= c.minDelta())
                    && (c.maxDelta() == null || delta <= c.maxDelta());
            if (!passed) {
                allPassed = false;
            }
            details.add(String.format(
                    "[%s] %s @ %s: expected %s, got %d (before=%d, after=%d)",
                    passed ? "PASS" : "FAIL", c.item(), c.chest().toShortString(),
                    formatExpectedRange(c.minDelta(), c.maxDelta()), delta, beforeCount, afterCount
            ));
        }

        String summary = allPassed
                ? "All container-content expectations met"
                : "Some container-content expectations failed";
        return new Result(allPassed, summary, details, new HashMap<>());
    }

    public static Map<String, Integer> computeDeltas(
            Map<String, Integer> beforeCounts,
            Map<String, Integer> afterCounts
    ) {
        Map<String, Integer> deltas = new HashMap<>();
        for (String key : afterCounts.keySet()) {
            deltas.put(key, afterCounts.get(key) - beforeCounts.getOrDefault(key, 0));
        }
        for (String key : beforeCounts.keySet()) {
            if (!afterCounts.containsKey(key)) {
                deltas.put(key, -beforeCounts.get(key));
            }
        }
        return deltas;
    }

    public static Result checkDeltas(Map<String, Integer> deltas, TestExpectation expectation) {
        return check(new HashMap<>(), deltas, expectation);
    }

    public static Result check(
            Map<String, Integer> beforeCounts,
            Map<String, Integer> afterCounts,
            TestExpectation expectation
    ) {
        List<String> details = new ArrayList<>();
        Map<String, Integer> deltas = new HashMap<>();
        details.add("Before: " + beforeCounts);
        details.add("After: " + afterCounts);

        boolean allPassed = true;

        for (TestExpectation.ExpectedProduct product : expectation.products()) {
            if ("*".equals(product.itemRegistryName())) {
                int beforeTotal = beforeCounts.values().stream().mapToInt(Integer::intValue).sum();
                int afterTotal = afterCounts.values().stream().mapToInt(Integer::intValue).sum();
                int delta = afterTotal - beforeTotal;
                deltas.put("*", delta);

                boolean productPassed = (product.minDelta() == null || delta >= product.minDelta())
                        && (product.maxDelta() == null || delta <= product.maxDelta());
                String status = productPassed ? "PASS" : "FAIL";
                String expectedRange = formatExpectedRange(product);
                details.add(String.format(
                        "[%s] %s: expected %s, got %d (before=%d, after=%d)",
                        status, "*", expectedRange, delta, beforeTotal, afterTotal
                ));
                if (!productPassed) {
                    allPassed = false;
                }
                continue;
            }

            int beforeCount = beforeCounts.getOrDefault(product.itemRegistryName(), 0);
            int afterCount = afterCounts.getOrDefault(product.itemRegistryName(), 0);
            int delta = afterCount - beforeCount;
            deltas.put(product.itemRegistryName(), delta);

            boolean productPassed = (product.minDelta() == null || delta >= product.minDelta())
                    && (product.maxDelta() == null || delta <= product.maxDelta());
            String status = productPassed ? "PASS" : "FAIL";
            String expectedRange = formatExpectedRange(product);
            details.add(String.format(
                    "[%s] %s: expected %s, got %d (before=%d, after=%d)",
                    status, product.itemRegistryName(), expectedRange, delta, beforeCount, afterCount
            ));
            if (!productPassed) {
                allPassed = false;
            }
        }

        boolean hasOptionalProduct = expectation.products().stream()
                .anyMatch(p -> (p.minDelta() == null || p.minDelta() == 0) && !"*".equals(p.itemRegistryName()));
        if (expectation.minCyclesExpected() > 0 && hasOptionalProduct) {
            int totalProduction = deltas.values().stream().filter(d -> d > 0).mapToInt(Integer::intValue).sum();
            boolean cyclePassed = totalProduction >= expectation.minCyclesExpected()
                    && (expectation.maxCyclesExpected() < 0 || totalProduction <= expectation.maxCyclesExpected());
            String status = cyclePassed ? "PASS" : "FAIL";
            details.add(String.format(
                    "[%s] total production: expected >= %d, got %d",
                    status, expectation.minCyclesExpected(), totalProduction
            ));
            if (!cyclePassed) {
                allPassed = false;
            }
        }

        String summary = allPassed ? "All expectations met" : "Some expectations failed";
        return new Result(allPassed, summary, details, deltas);
    }

    private static String formatExpectedRange(TestExpectation.ExpectedProduct product) {
        return formatExpectedRange(product.minDelta(), product.maxDelta());
    }

    private static String formatExpectedRange(Integer minDelta, Integer maxDelta) {
        if (minDelta != null && maxDelta != null) {
            return minDelta + ".." + maxDelta;
        }
        if (minDelta != null) {
            return ">= " + minDelta;
        }
        if (maxDelta != null) {
            return "<= " + maxDelta;
        }
        return "any";
    }

    private static String itemRegistryName(MCTownItem item) {
        String shortName = item.getShortName();
        int xIndex = shortName.indexOf('x');
        if (xIndex > 0 && xIndex < shortName.length() - 1) {
            String prefix = shortName.substring(0, xIndex);
            if (prefix.chars().allMatch(Character::isDigit)) {
                return shortName.substring(xIndex + 1);
            }
        }
        return shortName;
    }
}
