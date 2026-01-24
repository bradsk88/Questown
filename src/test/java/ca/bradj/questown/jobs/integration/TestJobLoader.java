package ca.bradj.questown.jobs.integration;

import ca.bradj.questown.jobs.JobDefinition;
import ca.bradj.questown.jobs.JobID;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * MC-free JSON parser that creates JobDefinition from questown_jobs JSON files.
 */
public class TestJobLoader {

    private static final Gson GSON = new Gson();

    /**
     * Load a job definition from a JSON file in the resources folder.
     *
     * @param resourcePath path relative to resources, e.g., "data/questown/questown_jobs/baker_bread.json"
     * @return parsed JobDefinition
     */
    public static JobDefinition loadFromFile(String resourcePath) {
        try (InputStream is = TestJobLoader.class.getClassLoader().getResourceAsStream(resourcePath)) {
            if (is == null) {
                throw new IllegalArgumentException("Resource not found: " + resourcePath);
            }
            JsonObject obj = GSON.fromJson(new InputStreamReader(is, StandardCharsets.UTF_8), JsonObject.class);
            return parseJobDefinition(obj);
        } catch (IOException e) {
            throw new RuntimeException("Failed to load job from " + resourcePath, e);
        }
    }

    /**
     * Parse a JsonObject into a JobDefinition.
     */
    public static JobDefinition parseJobDefinition(JsonObject obj) {
        String idStr = obj.get("id").getAsString();
        JobID jobId = JobID.fromJSON(idStr);

        JsonArray workStates = obj.getAsJsonArray("work_states");
        ImmutableMap.Builder<Integer, String> ingredients = ImmutableMap.builder();
        ImmutableMap.Builder<Integer, Integer> ingredientQty = ImmutableMap.builder();
        ImmutableMap.Builder<Integer, String> tools = ImmutableMap.builder();
        ImmutableMap.Builder<Integer, Integer> work = ImmutableMap.builder();
        ImmutableMap.Builder<Integer, Integer> time = ImmutableMap.builder();

        int maxState = 0;
        for (int i = 0; i < workStates.size(); i++) {
            JsonObject state = workStates.get(i).getAsJsonObject();
            boolean hasContent = false;

            if (state.has("ingredients")) {
                ingredients.put(i, state.get("ingredients").getAsString());
                ingredientQty.put(i, state.has("quantity") ? state.get("quantity").getAsInt() : 1);
                hasContent = true;
            }

            if (state.has("tools")) {
                tools.put(i, state.get("tools").getAsString());
                hasContent = true;
            }

            if (state.has("work")) {
                work.put(i, state.get("work").getAsInt());
                hasContent = true;
            }

            if (state.has("time")) {
                time.put(i, state.get("time").getAsInt());
                hasContent = true;
            }

            if (hasContent) {
                maxState = i + 1;
            }
        }

        String result = getResultItem(obj);

        // Parse special rules
        ImmutableList.Builder<String> globalRules = ImmutableList.builder();
        ImmutableMap.Builder<Integer, Collection<String>> stateRules = ImmutableMap.builder();

        if (obj.has("special")) {
            JsonArray specialArray = obj.getAsJsonArray("special");
            for (JsonElement elem : specialArray) {
                JsonObject special = elem.getAsJsonObject();
                String type = special.get("type").getAsString();
                JsonArray rulesArray = special.getAsJsonArray("rules");
                List<String> rules = new ArrayList<>();
                for (JsonElement rule : rulesArray) {
                    rules.add(rule.getAsString());
                }

                if ("global".equals(type)) {
                    globalRules.addAll(rules);
                } else if ("processing_state".equals(type)) {
                    int state = special.get("state").getAsInt();
                    stateRules.put(state, ImmutableList.copyOf(rules));
                }
            }
        }

        return new JobDefinition(
                jobId,
                maxState,
                ingredients.build(),
                ingredientQty.build(),
                tools.build(),
                work.build(),
                time.build(),
                result,
                globalRules.build(),
                stateRules.build()
        );
    }

    private static String getResultItem(JsonObject obj) {
        if (!obj.has("result")) {
            return "minecraft:air";
        }
        JsonObject result = obj.getAsJsonObject("result");
        String type = result.get("type").getAsString();

        return switch (type) {
            case "item" -> result.has("item") ? result.get("item").getAsString() : "minecraft:air";
            case "biome_loot", "loot", "crafting_table" -> "loot"; // Placeholder for dynamic results
            default -> "minecraft:air";
        };
    }

    /**
     * Get the result quantity from the JSON.
     */
    public static int getResultQuantity(JsonObject obj) {
        if (!obj.has("result")) {
            return 0;
        }
        JsonObject result = obj.getAsJsonObject("result");
        return result.has("quantity") ? result.get("quantity").getAsInt() : 1;
    }
}
