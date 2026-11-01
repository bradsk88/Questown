package ca.bradj.questown.jobs;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * The cook simple-furnace template expands (at server start, via the Forge
 * tag registry) to one job per raw item in
 * {@code questown:villager_simple_furnace_food_raw} ({@code cook/mutton},
 * {@code cook/beef}, ...). Exactly one of them — the item listed in the
 * template's {@code starter_recipes} — must join the root's initial unlock
 * set, so a freshly staffed cook (spawn or job-change) can actually cook
 * while the remaining recipes stay progression-gated.
 *
 * <p>The grant is the template's {@code starter_recipes}, so every staffing path gets it via
 * {@code ServerJobsRegistry.getDefaultWork}.
 */
class CookStarterRecipeLoaderTest {

    private static final String JOBS_PATH = "data/questown/questown_jobs/";
    private static final Gson GSON = new Gson();

    /**
     * Data-oracle half: the template JSON is what the production loader reads
     * at server start. This pins the load gate for "mutton is the starter"
     * without the Forge tag registry (which a plain unit test cannot load —
     * see the disabled test below).
     */
    @Test
    void template_listsMuttonInStarterRecipes() {
        JsonObject raw = readJson(JOBS_PATH + "cook_simple_furnace_food.json");
        JsonArray starters = raw.getAsJsonArray("starter_recipes");
        List<String> starterNames = java.util.stream.StreamSupport.stream(starters.spliterator(), false).map(JsonElement::getAsString).toList();
        Assertions.assertNotNull(starters,
                "cook_simple_furnace_food.json must carry a starter_recipes list "
                        + "(the 2026-10-12 playtest: every cook needs a starter recipe at staff)");
        Assertions.assertTrue(starterNames.size() >= 1, "starter_recipes must be non-empty");
        Assertions.assertTrue(starterNames.contains("mutton"),
                "mutton is the starter recipe (the first hunter's guaranteed simple-furnace food)");
        // Beef (and the other five foods) must NOT be listed
        Assertions.assertFalse(starterNames.contains("beef"),
                "beef stays progression-gated; a blanket starter list is the rejected bulk grant");
    }

    /**
     * Registry-bound half: the expanded jobs only materialize through
     * {@code ResourceJobLoader.makeCookJobs}, which iterates a questown item tag whose contents live
     * in the loader's tag registry; {@code Bootstrap.bootStrap()} does not populate it, so a unit
     * test expands to an empty job set.
     */
    @Test
    @Disabled("registry-bound: makeCookJobs expands via the questown item tag registry, which vanilla Bootstrap does not load; see Javadoc")
    void expandedJobs_muttonUnlocked_siblingsLocked() {
        Assertions.fail("registry-bound: the production loader's tag-registry expansion "
                + "(starter_recipes -> per-item unlocked_with_root_job) cannot run in a "
                + "plain unit test; the JSON load gate is pinned by template_listsMuttonInStarterRecipes");
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
