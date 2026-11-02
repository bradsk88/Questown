package ca.bradj.questown.blocks;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** The flag recipe: an upside-down T of cobblestone with a sapling in the middle, unlocked by the first cobblestone. */
class FlagRecipeTest {

    private static final Path DATA = Path.of("src/main/resources/data/questown");

    @Test
    void flag_isAnUpsideDownTOfCobblestoneWithASaplingWhereStemMeetsBar() throws IOException {
        JsonObject r = read(DATA.resolve("recipes/flag_base.json"));
        Assertions.assertEquals("minecraft:crafting_shaped", r.get("type").getAsString());
        List<String> rows = new ArrayList<>();
        r.getAsJsonArray("pattern").forEach(e -> rows.add(e.getAsString()));
        Assertions.assertEquals(List.of(" C ", " C ", "CSC"), rows);
        JsonObject key = r.getAsJsonObject("key");
        Assertions.assertEquals("minecraft:cobblestone", key.getAsJsonObject("C").get("item").getAsString());
        Assertions.assertEquals("minecraft:saplings", key.getAsJsonObject("S").get("tag").getAsString());
        Assertions.assertEquals("questown:flag_base", r.getAsJsonObject("result").get("id").getAsString());
    }

    @Test
    void flagRecipe_unlocksWithCobblestone() throws IOException {
        JsonObject a = read(DATA.resolve("advancements/recipes/flag_base.json"));
        Assertions.assertEquals("questown:flag_base",
                a.getAsJsonObject("rewards").getAsJsonArray("recipes").get(0).getAsString());
        Assertions.assertTrue(a.toString().contains("minecraft:cobblestone"), a.toString());
    }

    private static JsonObject read(Path p) throws IOException {
        return new Gson().fromJson(Files.readString(p, StandardCharsets.UTF_8), JsonObject.class);
    }
}
