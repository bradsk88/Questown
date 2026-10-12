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

/**
 * Pins the data wiring of the job-board ROOM quest (wanted id {@code questown:job_board}).
 *
 * <p>Completion depends on a specific chain: the quest's wanted id equals the room recipe
 * {@code questown:job_board} (data/questown/recipes/job_board.json), whose single ingredient
 * is the item tag {@code questown:job_board_inputs}, which contains the board block item.
 * Both sides of that chain feed the recipe-cost tie that decides which recipe "wins" a
 * board meta room (the real recipe vs the synthetic {@code block_room/...} recipe), and the
 * per-tick backfill ({@code TownRoomsHandle.getRoomsMatching}'s JOB_BOARD branch) maps the
 * quest id to the board's registered block-room key. A data edit that breaks any link
 * (direct-item ingredient, board item dropped from the tag, recipe id renamed) silently
 * changes quest-completion behavior - these assertions are the tripwire.
 */
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
