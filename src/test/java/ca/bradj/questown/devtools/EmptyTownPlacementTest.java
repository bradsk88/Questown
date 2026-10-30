package ca.bradj.questown.devtools;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/**
 * Dev playtest 2026-10-29: "I see that an empty_town spawned inside of a vanilla village. That
 * probably shouldn't happen". The structure set keeps away from village placements the way
 * vanilla's pillager outposts do.
 */
class EmptyTownPlacementTest {

    @Test
    void emptyTownKeepsAwayFromVillages() throws IOException {
        JsonObject placement = structureSet().getAsJsonObject("placement");
        JsonObject zone = placement.getAsJsonObject("exclusion_zone");
        Assertions.assertNotNull(zone, "empty_town placement has an exclusion_zone");
        Assertions.assertEquals("minecraft:villages", zone.get("other_set").getAsString());
        Assertions.assertTrue(zone.get("chunk_count").getAsInt() > 0, "exclusion radius in chunks");
    }

    private static JsonObject structureSet() throws IOException {
        String path = "data/questown/worldgen/structure_set/empty_town.json";
        try (InputStream in = EmptyTownPlacementTest.class.getClassLoader().getResourceAsStream(path)) {
            Assertions.assertNotNull(in, path + " on the test classpath");
            return new Gson().fromJson(new InputStreamReader(in, StandardCharsets.UTF_8), JsonObject.class);
        }
    }
}
