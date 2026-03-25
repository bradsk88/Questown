package ca.bradj.questown.commands.test;

import ca.bradj.questown.integration.minecraft.MCHeldItem;
import ca.bradj.questown.integration.minecraft.MCTownItem;
import ca.bradj.questown.integration.minecraft.MCTownState;
import ca.bradj.questown.jobs.leaver.ContainerTarget;
import ca.bradj.questown.town.TownState;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class TestResultChecker {

    public record Result(boolean passed, String summary, List<String> details, Map<String, Integer> deltas) {}

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

                boolean productPassed = delta >= product.minQuantity()
                        && (product.maxQuantity() < 0 || delta <= product.maxQuantity());
                String status = productPassed ? "PASS" : "FAIL";
                String expectedRange = product.maxQuantity() < 0
                        ? ">= " + product.minQuantity()
                        : product.minQuantity() + ".." + product.maxQuantity();
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

            boolean productPassed = delta >= product.minQuantity()
                    && (product.maxQuantity() < 0 || delta <= product.maxQuantity());
            String status = productPassed ? "PASS" : "FAIL";
            String expectedRange = product.maxQuantity() < 0
                    ? ">= " + product.minQuantity()
                    : product.minQuantity() + ".." + product.maxQuantity();
            details.add(String.format(
                    "[%s] %s: expected %s, got %d (before=%d, after=%d)",
                    status, product.itemRegistryName(), expectedRange, delta, beforeCount, afterCount
            ));
            if (!productPassed) {
                allPassed = false;
            }
        }

        String summary = allPassed ? "All expectations met" : "Some expectations failed";
        return new Result(allPassed, summary, details, deltas);
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
