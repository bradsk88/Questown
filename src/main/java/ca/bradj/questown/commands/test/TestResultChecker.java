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

    public record Result(boolean passed, String summary, List<String> details) {}

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

    public static Result check(
            Map<String, Integer> beforeCounts,
            Map<String, Integer> afterCounts,
            TestExpectation expectation
    ) {
        List<String> details = new ArrayList<>();
        details.add("Before: " + beforeCounts);
        details.add("After: " + afterCounts);

        boolean allPassed = true;

        for (TestExpectation.ExpectedProduct product : expectation.products()) {
            int beforeCount = beforeCounts.getOrDefault(product.itemRegistryName(), 0);
            int afterCount = afterCounts.getOrDefault(product.itemRegistryName(), 0);
            int delta = afterCount - beforeCount;

            boolean productPassed = delta >= product.minQuantity();
            String status = productPassed ? "PASS" : "FAIL";
            details.add(String.format(
                    "[%s] %s: expected >= %d, got %d (before=%d, after=%d)",
                    status, product.itemRegistryName(), product.minQuantity(), delta, beforeCount, afterCount
            ));
            if (!productPassed) {
                allPassed = false;
            }
        }

        String summary = allPassed ? "All expectations met" : "Some expectations failed";
        return new Result(allPassed, summary, details);
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
