package ca.bradj.questown.jobs.gatherer;

import com.google.common.collect.ImmutableSet;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.Map;

/**
 * The items a leaver's loot tables can drop, read from the table JSON (every {@code minecraft:item}
 * entry, nested pools and alternatives included). Player-facing producer lists use it so a job
 * counts as producing an item only when its own table can drop it: the town's known gather
 * results are not scoped per table, so without this every biome-loot job claimed every other
 * job's finds ("20 known jobs produce wheat seeds", five do; dev 2026-10-22: narrow the count
 * only, the extraction gate stays as it is).
 */
public final class LootTableDrops {

    private LootTableDrops() {
    }

    /** Item ids every table in {@code tables} can drop. */
    public static ImmutableSet<String> itemIds(Iterable<JsonElement> tables) {
        ImmutableSet.Builder<String> b = ImmutableSet.builder();
        for (JsonElement table : tables) {
            collect(table, b);
        }
        return b.build();
    }

    private static void collect(JsonElement e, ImmutableSet.Builder<String> out) {
        if (e instanceof JsonArray arr) {
            arr.forEach(c -> collect(c, out));
            return;
        }
        if (!(e instanceof JsonObject obj)) {
            return;
        }
        if (isItemEntry(obj)) {
            out.add(obj.get("name").getAsString());
        }
        for (Map.Entry<String, JsonElement> child : obj.entrySet()) {
            collect(child.getValue(), out);
        }
    }

    private static boolean isItemEntry(JsonObject obj) {
        JsonElement type = obj.get("type");
        JsonElement name = obj.get("name");
        return type != null && type.isJsonPrimitive() && "minecraft:item".equals(type.getAsString())
                && name != null && name.isJsonPrimitive();
    }
}
