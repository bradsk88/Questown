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

/** Only the tool-tier crafter jobs declare {@code available_at_level}; every other job uses the default. */
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
                "wood:3", "stone:5", "iron:8", "golden:11", "diamond:14"
        );
        for (String expectation : expectations) {
            String tier = expectation.split(":")[0];
            int level = Integer.parseInt(expectation.split(":")[1]);
            for (String tool : TOOLS) {
                JsonObject job = read("blacksmith_" + tier + "_" + tool + ".json");
                // The wooden pickaxe unlocks at level 2.
                int expected = tier.equals("wood") && tool.equals("pickaxe") ? 2 : level;
                Assertions.assertEquals(
                        expected,
                        job.get("available_at_level").getAsInt(),
                        "blacksmith_" + tier + "_" + tool + ".json must declare available_at_level " + expected
                );
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
