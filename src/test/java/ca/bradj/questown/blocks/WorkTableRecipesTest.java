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

    /**
     * Dev 2026-10-28: "Armorers Table --> [AT, Chest] --> [AT, Chest, Stand] --> [AT, Chest, four
     * stands]". A room is every recipe it contains and its identity is the heaviest, so each tier
     * must contain the one before.
     */
    @Test
    void armoryTiers_eachContainTheTierBefore() throws IOException {
        List<List<String>> tiers = List.of(
                items(read("armorers_corner.json")),
                items(read("armory.json")),
                items(read("armor_display.json")),
                items(read("grand_armory.json"))
        );
        Assertions.assertEquals(List.of("questown:armorers_table"), tiers.get(0));
        Assertions.assertEquals(List.of("questown:armorers_table", "#forge:chests"), tiers.get(1));
        Assertions.assertEquals(List.of("questown:armorers_table", "#forge:chests", "minecraft:armor_stand"), tiers.get(2));
        Assertions.assertEquals(4, tiers.get(3).stream().filter("minecraft:armor_stand"::equals).count());
        assertNested(tiers);
    }

    /** Dev 2026-10-28: "the blacksmith probably has a similar setup, but with item frames". */
    @Test
    void smithyTiers_eachContainTheTierBefore() throws IOException {
        List<List<String>> tiers = List.of(
                items(read("smithy.json")),
                items(read("smithy_stocked.json")),
                items(read("weapon_display.json")),
                items(read("grand_smithy.json"))
        );
        Assertions.assertTrue(tiers.get(2).contains("minecraft:item_frame"), tiers.get(2).toString());
        Assertions.assertEquals(4, tiers.get(3).stream().filter("minecraft:item_frame"::equals).count());
        assertNested(tiers);
    }

    private static void assertNested(List<List<String>> tiers) {
        for (int i = 1; i < tiers.size(); i++) {
            List<String> rest = new ArrayList<>(tiers.get(i));
            for (String ing : tiers.get(i - 1)) {
                Assertions.assertTrue(rest.remove(ing), "tier " + i + " lacks " + ing + " from the tier before");
            }
            Assertions.assertFalse(rest.isEmpty(), "tier " + i + " adds nothing");
        }
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
                // The lowest tier, so the jobs work in every armory tier.
                Assertions.assertEquals("questown:armorers_corner", job.get("room").getAsString(), f.getFileName().toString());
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
        Assertions.assertEquals(result, r.getAsJsonObject("result").get("item").getAsString());
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
