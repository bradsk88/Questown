package ca.bradj.questown.jobs;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.Reader;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

/**
 * Outfitter playtest #21 (dev 2026-10-26): the Armor batch's "Leather x4" took ~40 short trips at
 * forest's 10.4%. Every hunter table that drops leather gives it at least a quarter of the roll
 * (~16 trips for four). Jungle has no leather and stays that way.
 */
class HunterLeatherDataTest {

    private static final Gson GSON = new Gson();

    @Test
    void everyHunterTableWithLeatherGivesItAtLeastAQuarterOfTheRoll() throws IOException, URISyntaxException {
        URL dir = HunterLeatherDataTest.class.getClassLoader().getResource("data/questown/loot_tables/jobs/hunter_sword");
        Assertions.assertNotNull(dir);
        List<Path> tables;
        try (Stream<Path> files = Files.walk(Path.of(dir.toURI()))) {
            tables = files.filter(f -> f.toString().endsWith(".json")).toList();
        }
        Assertions.assertTrue(tables.size() >= 9, "hunter tables found: " + tables.size());
        for (Path p : tables) {
            int total = 0;
            int leather = 0;
            try (Reader r = Files.newBufferedReader(p, StandardCharsets.UTF_8)) {
                JsonObject table = GSON.fromJson(r, JsonObject.class);
                for (JsonElement e : table.getAsJsonArray("pools").get(0).getAsJsonObject().getAsJsonArray("entries")) {
                    JsonObject entry = e.getAsJsonObject();
                    int w = entry.has("weight") ? entry.get("weight").getAsInt() : 1;
                    total += w;
                    if ("minecraft:leather".equals(entry.get("name").getAsString())) {
                        leather += w;
                    }
                }
            }
            if (leather == 0) {
                continue;
            }
            double share = (double) leather / total;
            Assertions.assertTrue(share >= 0.249, p.getFileName() + " leather share " + share);
        }
    }
}
