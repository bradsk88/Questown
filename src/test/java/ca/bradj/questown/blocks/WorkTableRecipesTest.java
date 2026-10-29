package ca.bradj.questown.blocks;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Dev playtest 2026-10-28: "an armory IS a smithy" (both rooms were built around the blacksmith's
 * table), and nothing told a player how to get the table. The armory now uses its own armorer's
 * table, and both tables are crafted from a crafting table and 4 Blocks of Progress (dev's
 * shapes: plus for the blacksmith's, corners for the armorer's).
 */
class WorkTableRecipesTest {

    private static final Path RECIPES = Path.of("src/main/resources/data/questown/recipes");

    @Test
    void armoryAndSmithy_doNotShareAWorkBlock() throws IOException {
        List<String> armory = items(read("armory.json"));
        List<String> smithy = items(read("smithy.json"));
        Assertions.assertTrue(armory.contains("questown:armorers_table"), armory.toString());
        Assertions.assertTrue(smithy.contains("questown:blacksmiths_table"), smithy.toString());
        Assertions.assertFalse(armory.contains("questown:blacksmiths_table"), armory.toString());
    }

    @Test
    void blacksmithsTable_isACraftingTableWithBlocksOfProgressLeftRightUpDown() throws IOException {
        assertShape("blacksmiths_table_from_blocks_of_progress.json", List.of(" B ", "BCB", " B "), "questown:blacksmiths_table");
    }

    @Test
    void armorersTable_isACraftingTableWithBlocksOfProgressInTheCorners() throws IOException {
        assertShape("armorers_table.json", List.of("B B", " C ", "B B"), "questown:armorers_table");
    }

    @Test
    void everyArmorerJob_worksAtTheArmorersTable() throws IOException {
        try (var files = Files.list(Path.of("src/main/resources/data/questown/questown_jobs"))) {
            for (Path f : files.filter(p -> p.getFileName().toString().startsWith("armorer_")).toList()) {
                JsonObject job = new Gson().fromJson(Files.readString(f, StandardCharsets.UTF_8), JsonObject.class);
                Assertions.assertEquals("questown:armorers_table", job.get("block").getAsString(), f.getFileName().toString());
            }
        }
    }

    private static void assertShape(String file, List<String> pattern, String result) throws IOException {
        JsonObject r = read(file);
        List<String> rows = new ArrayList<>();
        r.getAsJsonArray("pattern").forEach(e -> rows.add(e.getAsString()));
        Assertions.assertEquals(pattern, rows);
        JsonObject key = r.getAsJsonObject("key");
        Assertions.assertEquals("questown:block_of_progress", key.getAsJsonObject("B").get("item").getAsString());
        Assertions.assertEquals("minecraft:crafting_table", key.getAsJsonObject("C").get("item").getAsString());
        Assertions.assertEquals(result, r.getAsJsonObject("result").get("id").getAsString());
    }

    private static List<String> items(JsonObject roomRecipe) {
        List<String> out = new ArrayList<>();
        JsonArray ingredients = roomRecipe.getAsJsonArray("ingredients");
        for (JsonElement e : ingredients) {
            JsonObject o = e.getAsJsonObject();
            out.add(o.has("item") ? o.get("item").getAsString() : "#" + o.get("tag").getAsString());
        }
        return out;
    }

    private static JsonObject read(String file) throws IOException {
        return new Gson().fromJson(Files.readString(RECIPES.resolve(file), StandardCharsets.UTF_8), JsonObject.class);
    }
}
