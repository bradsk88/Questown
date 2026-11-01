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
 * An empty_town keeps away from village placements, the way vanilla's pillager outposts do.
 */
class EmptyTownPlacementTest {

    @Test
    void emptyTownKeepsAwayFromVillages() throws IOException {
        JsonObject placement = structureSet().getAsJsonObject("placement");
        JsonObject zone = placement.getAsJsonObject("exclusion_zone");
        Assertions.assertNotNull(zone, "empty_town placement has an exclusion_zone");
        Assertions.assertEquals("minecraft:villages", zone.get("other_set").getAsString());
        // 4 chunks: the outposts' 10 removed a third of all empty towns on a measured seed, most of
        // them nowhere near a village.
        Assertions.assertEquals(4, zone.get("chunk_count").getAsInt(), "exclusion radius in chunks");
    }

    private static JsonObject structureSet() throws IOException {
        String path = "data/questown/worldgen/structure_set/empty_town.json";
        try (InputStream in = EmptyTownPlacementTest.class.getClassLoader().getResourceAsStream(path)) {
            Assertions.assertNotNull(in, path + " on the test classpath");
            return new Gson().fromJson(new InputStreamReader(in, StandardCharsets.UTF_8), JsonObject.class);
        }
    }
}
