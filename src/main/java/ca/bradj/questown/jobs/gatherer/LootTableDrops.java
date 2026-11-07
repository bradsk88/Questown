package ca.bradj.questown.jobs.gatherer;

import com.google.common.collect.ImmutableSet;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.Map;

/** The items a trip's loot tables can drop, read from the table files. A job produces an item only if its own tables can drop it. */
public final class LootTableDrops {

    private LootTableDrops() {
    }

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
