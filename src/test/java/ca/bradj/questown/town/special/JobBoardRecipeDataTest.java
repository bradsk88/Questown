package ca.bradj.questown.town.special;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The job board quest's wanted id is the {@code questown:job_board} recipe, whose ingredient tag contains the board item. A data change that breaks this fails here. */
class JobBoardRecipeDataTest {

    private static JsonObject resource(String path) throws Exception {
        try (InputStream in = JobBoardRecipeDataTest.class.getClassLoader()
                .getResourceAsStream("data/questown/" + path)) {
            assertTrue(in != null, "missing resource: data/questown/" + path);
            String s = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            return JsonParser.parseString(s).getAsJsonObject();
        }
    }

    @Test
    void jobBoardRecipeIsARoomRecipeOnTheBoardTag() throws Exception {
        JsonObject recipe = resource("recipes/job_board.json");
        assertEquals("roomrecipes:room", recipe.get("type").getAsString(),
                "the board quest's wanted id must be a ROOM recipe");
        JsonArray ingredients = recipe.getAsJsonArray("ingredients");
        assertEquals(1, ingredients.size(),
                "a single-ingredient recipe keeps the board room's cost deterministic");
        JsonObject ing = ingredients.get(0).getAsJsonObject();
        assertTrue(ing.has("tag") && !ing.has("item"),
                "the ingredient must be the board tag, not a direct item");
        assertEquals("questown:job_board_inputs", ing.get("tag").getAsString());
    }

    @Test
    void boardTagContainsTheBoardBlockItem() throws Exception {
        JsonObject tag = resource("tags/items/job_board_inputs.json");
        JsonArray values = tag.getAsJsonArray("values");
        for (JsonElement v : values) {
            String id = v.isJsonObject() ? v.getAsJsonObject().get("id").getAsString() : v.getAsString();
            if ("questown:job_board_block".equals(id)) {
                return;
            }
        }
        assertFalse(true, "questown:job_board_block missing from #questown:job_board_inputs");
    }
}
