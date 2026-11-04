package ca.bradj.questown.jobs;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;

/** The cook template expands to one job per raw food; only the one in {@code starter_recipes} joins a new cook's starting jobs. */
class CookStarterRecipeLoaderTest {

    private static final String JOBS_PATH = "data/questown/questown_jobs/";
    private static final Gson GSON = new Gson();

    /** The template file lists mutton, and only mutton, as the starter. */
    @Test
    void template_listsMuttonInStarterRecipes() {
        JsonObject raw = readJson(JOBS_PATH + "cook_simple_furnace_food.json");
        JsonArray starters = raw.getAsJsonArray("starter_recipes");
        List<String> starterNames = starters.asList().stream().map(JsonElement::getAsString).toList();
        Assertions.assertNotNull(starters,
                "cook_simple_furnace_food.json must carry a starter_recipes list: every cook needs a starter recipe");
        Assertions.assertTrue(starterNames.size() >= 1, "starter_recipes must be non-empty");
        Assertions.assertTrue(starterNames.contains("mutton"),
                "mutton is the starter recipe (the first hunter's guaranteed simple-furnace food)");
        Assertions.assertFalse(starterNames.contains("beef"),
                "beef stays progression-gated; a blanket starter list is the rejected bulk grant");
    }

    private static JsonObject readJson(String resourcePath) {
        try (InputStream is = CookStarterRecipeLoaderTest.class.getClassLoader().getResourceAsStream(resourcePath)) {
            Assertions.assertNotNull(is, "resource not found: " + resourcePath);
            return GSON.fromJson(new InputStreamReader(is, StandardCharsets.UTF_8), JsonObject.class);
        } catch (java.io.IOException e) {
            throw new RuntimeException("Failed to read " + resourcePath, e);
        }
    }
}
