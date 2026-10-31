package ca.bradj.questown.gui;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;

/**
 * A room quest card names each tag ingredient through the {@code "#<tag>"} lang key
 * ({@link Ingredients#getName}). A tag without one shows its raw id. 2026-10-21 agent playtest:
 * the storeroom's chest read {@code #c:chests}, because the lang file still named the 1.19.2
 * {@code #forge:chests}.
 */
class RecipeTagNamesTest {

    private static final Path RECIPES = Path.of("src/main/resources/data/questown/recipes");
    private static final Path LANG = Path.of("src/main/resources/assets/questown/lang/en_us.json");

    @Test
    void everyRoomRecipeTag_hasAName() throws IOException {
        JsonObject lang = JsonParser.parseString(Files.readString(LANG)).getAsJsonObject();
        Set<String> unnamed = new TreeSet<>();
        for (String tag : roomRecipeTags()) {
            if (!lang.has("#" + tag)) {
                unnamed.add(tag);
            }
        }
        Assertions.assertEquals(Set.of(), unnamed);
    }

    private static Set<String> roomRecipeTags() throws IOException {
        Set<String> tags = new TreeSet<>();
        List<Path> files;
        try (Stream<Path> s = Files.list(RECIPES)) {
            files = s.filter(p -> p.toString().endsWith(".json")).toList();
        }
        for (Path f : files) {
            JsonObject recipe = JsonParser.parseString(Files.readString(f)).getAsJsonObject();
            if (!recipe.has("type") || !"roomrecipes:room".equals(recipe.get("type").getAsString())) {
                continue;
            }
            for (JsonElement ing : recipe.getAsJsonArray("ingredients")) {
                if (ing.isJsonObject() && ing.getAsJsonObject().has("tag")) {
                    tags.add(ing.getAsJsonObject().get("tag").getAsString());
                }
            }
        }
        Assertions.assertFalse(tags.isEmpty(), "no recipe tags found under " + RECIPES);
        return tags;
    }
}
