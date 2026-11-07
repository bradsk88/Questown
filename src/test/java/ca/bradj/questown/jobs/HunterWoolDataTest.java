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

/** Every hunter table gives white wool about a tenth of the roll. */
class HunterWoolDataTest {

    private static final Gson GSON = new Gson();

    @Test
    void everyHunterTableGivesWoolAboutOneRollInTen() throws IOException, URISyntaxException {
        URL dir = HunterWoolDataTest.class.getClassLoader().getResource("data/questown/loot_tables/jobs/hunter_sword");
        Assertions.assertNotNull(dir);
        List<Path> tables;
        try (Stream<Path> files = Files.walk(Path.of(dir.toURI()))) {
            tables = files.filter(f -> f.toString().endsWith(".json")).toList();
        }
        Assertions.assertTrue(tables.size() >= 9, "hunter tables found: " + tables.size());
        for (Path p : tables) {
            int total = 0;
            int wool = 0;
            try (Reader r = Files.newBufferedReader(p, StandardCharsets.UTF_8)) {
                JsonObject table = GSON.fromJson(r, JsonObject.class);
                for (JsonElement e : table.getAsJsonArray("pools").get(0).getAsJsonObject().getAsJsonArray("entries")) {
                    JsonObject entry = e.getAsJsonObject();
                    int w = entry.has("weight") ? entry.get("weight").getAsInt() : 1;
                    total += w;
                    if ("minecraft:white_wool".equals(entry.get("name").getAsString())) {
                        wool += w;
                    }
                }
            }
            double share = (double) wool / total;
            Assertions.assertTrue(share >= 0.08 && share <= 0.12, p.getFileName() + " wool share " + share);
        }
    }
}
