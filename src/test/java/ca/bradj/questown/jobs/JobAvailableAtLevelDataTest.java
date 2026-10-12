package ca.bradj.questown.jobs;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

/**
 * Data-oracle half of the skills-tab level gate (2026-10-13 ruling): the material-tier
 * crafter jobs (the "blacksmith" unlock group) declare their {@code available_at_level} in
 * the job JSON, and NOTHING else does - every other job stays on the default (2, the first
 * level the tab appears). The JSON files are what {@code ResourceJobLoader} reads at server
 * start, so pinning the values here pins the gate without the Forge registry.
 */
class JobAvailableAtLevelDataTest {

    private static final Gson GSON = new Gson();
    private static final Path JOBS_DIR = Path.of("src", "main", "resources", "data", "questown", "questown_jobs");

    private static final String[] TOOLS = {"axe", "shovel", "pickaxe", "hoe", "sword"};

    private static JsonObject read(String file) throws Exception {
        try (InputStream in = Files.newInputStream(JOBS_DIR.resolve(file));
             InputStreamReader r = new InputStreamReader(in, StandardCharsets.UTF_8)) {
            return GSON.fromJson(r, JsonObject.class);
        }
    }

    @Test
    void materialTierJobs_declareTheExpectedLevels() throws Exception {
        List<String> expectations = List.of(
                "wood:7", "stone:12", "iron:17", "golden:22", "diamond:27"
        );
        for (String expectation : expectations) {
            String tier = expectation.split(":")[0];
            int level = Integer.parseInt(expectation.split(":")[1]);
            for (String tool : TOOLS) {
                JsonObject job = read("blacksmith_" + tier + "_" + tool + ".json");
                Assertions.assertEquals(
                        level,
                        job.get("available_at_level").getAsInt(),
                        "blacksmith_" + tier + "_" + tool + ".json must declare available_at_level " + level
                );
                // the id must stay in the crafter family - the gate rides the job, not the file name
                Assertions.assertTrue(job.get("id").getAsString().startsWith("crafter/"),
                        "the blacksmith tier files define crafter jobs: " + job.get("id"));
            }
        }
    }

    @Test
    void onlyMaterialTierJobs_carryTheField() throws Exception {
        int tierFilesWithField = 0;
        try (Stream<Path> files = Files.list(JOBS_DIR)) {
            for (Path f : (Iterable<Path>) files.filter(p -> p.toString().endsWith(".json"))::iterator) {
                String name = f.getFileName().toString();
                JsonObject job = GSON.fromJson(
                        new InputStreamReader(Files.newInputStream(f), StandardCharsets.UTF_8),
                        JsonObject.class
                );
                boolean isTier = name.startsWith("blacksmith_");
                if (job.has("available_at_level")) {
                    tierFilesWithField++;
                    Assertions.assertTrue(isTier,
                            "unexpected available_at_level on a non-tier job: " + name);
                } else {
                    Assertions.assertFalse(isTier,
                            "a tier file without available_at_level: " + name);
                }
            }
        }
        Assertions.assertEquals(25, tierFilesWithField, "the five tool tiers x five tools carry the field");
    }
}
