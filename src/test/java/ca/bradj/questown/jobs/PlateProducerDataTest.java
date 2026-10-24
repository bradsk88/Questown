package ca.bradj.questown.jobs;

import com.google.gson.Gson;
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
 * The Dinner Plate had no source: no crafting recipe and no job made it, so a dining room could
 * only be furnished in creative (r16 built one from creative plates). Dev ruling 2026-10-24: a
 * crafter makes plates. That is what lets the plate carry a real room-recipe weight
 * ({@code RecipeItemConfig}) instead of "never recommend".
 */
class PlateProducerDataTest {

    private static final Gson GSON = new Gson();
    private static final String PLATE = "questown:plate_block";

    @Test
    void aCrafterJobMakesPlates() throws IOException, URISyntaxException {
        List<String> producers = jobs().stream()
                .filter(j -> PLATE.equals(resultItem(j)))
                .map(j -> j.get("id").getAsString())
                .toList();

        Assertions.assertEquals(List.of("crafter/plate"), producers);
    }

    private static String resultItem(JsonObject job) {
        if (!job.has("result") || !job.get("result").isJsonObject()) {
            return null;
        }
        JsonObject result = job.getAsJsonObject("result");
        if (!result.has("item")) {
            return null;
        }
        return result.get("item").getAsString();
    }

    private static List<JsonObject> jobs() throws IOException, URISyntaxException {
        URL dir = PlateProducerDataTest.class.getClassLoader().getResource("data/questown/questown_jobs");
        Assertions.assertNotNull(dir, "job data directory on the test classpath");
        try (Stream<Path> files = Files.list(Path.of(dir.toURI()))) {
            List<Path> paths = files.filter(f -> f.toString().endsWith(".json")).toList();
            List<JsonObject> out = new java.util.ArrayList<>();
            for (Path p : paths) {
                try (Reader r = Files.newBufferedReader(p, StandardCharsets.UTF_8)) {
                    out.add(GSON.fromJson(r, JsonObject.class));
                }
            }
            return out;
        }
    }
}
