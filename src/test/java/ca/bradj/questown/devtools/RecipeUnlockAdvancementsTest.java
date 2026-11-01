package ca.bradj.questown.devtools;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * The armorer's and blacksmith's tables show in the crafting table's recipe book: a recipe shows
 * there only once a recipe advancement unlocks it, as vanilla's {@code advancements/recipes/...} do.
 */
class RecipeUnlockAdvancementsTest {

    private static final List<String> BOOK_RECIPES = List.of(
            "armorers_table",
            "blacksmiths_table",
            "blacksmiths_table_from_blocks_of_progress"
    );

    @Test
    void eachTableRecipe_isUnlockedByAnAdvancement_namingAnExistingRecipe() throws IOException {
        for (String name : BOOK_RECIPES) {
            JsonObject adv = json("data/questown/advancements/recipes/" + name + ".json");
            JsonElement rewarded = adv.getAsJsonObject("rewards").getAsJsonArray("recipes").get(0);
            Assertions.assertEquals("questown:" + name, rewarded.getAsString());
            Assertions.assertNotNull(json("data/questown/recipes/" + name + ".json"), name + " recipe exists");
        }
    }

    private static JsonObject json(String path) throws IOException {
        try (InputStream in = RecipeUnlockAdvancementsTest.class.getClassLoader().getResourceAsStream(path)) {
            Assertions.assertNotNull(in, path + " on the test classpath");
            return new Gson().fromJson(new InputStreamReader(in, StandardCharsets.UTF_8), JsonObject.class);
        }
    }
}
