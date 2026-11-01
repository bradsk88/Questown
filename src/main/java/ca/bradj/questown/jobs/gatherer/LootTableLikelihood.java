package ca.bradj.questown.jobs.gatherer;

import com.google.common.collect.ImmutableList;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A loot table's item ids, most likely first (a skill's "Produces:" lists the three most likely).
 * An item's likelihood is its expected count per roll of the table: in each pool, the rolls times
 * its weight's share of the pool's total weight (an entry's weight defaults to 1; empty and tag
 * entries take their share and are not listed), summed over pools. Ties keep the table's order.
 * Read from the table JSON, so it needs no registry.
 */
public final class LootTableLikelihood {

    private LootTableLikelihood() {
    }

    public static ImmutableList<String> itemIdsByLikelihood(JsonElement table) {
        Map<String, Double> chances = new LinkedHashMap<>();
        if (!(table instanceof JsonObject obj) || !(obj.get("pools") instanceof JsonArray pools)) {
            return ImmutableList.of();
        }
        for (JsonElement pool : pools) {
            if (pool instanceof JsonObject p) {
                addPool(p, chances);
            }
        }
        return chances.entrySet().stream()
                      .sorted(Map.Entry.<String, Double>comparingByValue(Comparator.reverseOrder()))
                      .map(Map.Entry::getKey)
                      .collect(ImmutableList.toImmutableList());
    }

    private static void addPool(JsonObject pool, Map<String, Double> chances) {
        if (!(pool.get("entries") instanceof JsonArray entries)) {
            return;
        }
        double total = 0;
        for (JsonElement e : entries) {
            total += weight(e);
        }
        if (total <= 0) {
            return;
        }
        double rolls = rolls(pool.get("rolls"));
        for (JsonElement e : entries) {
            String id = itemId(e);
            if (id != null) {
                chances.merge(id, rolls * weight(e) / total, Double::sum);
            }
        }
    }

    private static double weight(JsonElement entry) {
        if (entry instanceof JsonObject o && o.has("weight") && o.get("weight").isJsonPrimitive()) {
            return o.get("weight").getAsDouble();
        }
        return 1;
    }

    /** A number, or a uniform {min, max} provider (its mean); anything else counts as one roll. */
    private static double rolls(JsonElement rolls) {
        if (rolls != null && rolls.isJsonPrimitive()) {
            return rolls.getAsDouble();
        }
        if (rolls instanceof JsonObject o && o.has("min") && o.has("max")) {
            return (o.get("min").getAsDouble() + o.get("max").getAsDouble()) / 2;
        }
        return 1;
    }

    private static String itemId(JsonElement entry) {
        if (!(entry instanceof JsonObject o) || !o.has("type") || !o.has("name")) {
            return null;
        }
        if (!"minecraft:item".equals(o.get("type").getAsString())) {
            return null;
        }
        return o.get("name").getAsString();
    }
}
