package ca.bradj.questown.jobs;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.SharedConstants;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.util.profiling.InactiveProfiler;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Stream;

/**
 * The optional {@code experience} job field (dev ruling 2026-10-24): XP per completion, used
 * instead of the formula ({@code DeclarativeJob.getExperienceEarned}: work units x action length +
 * a tenth of the timed duration, at least 1). Every cook and baker job declares
 * {@code "work": 0.1}, which the loader reads with {@code getAsInt()} as 0, so each completion
 * earned 1 XP (logs: 267 "[Experience] +1 from cook"); the dev set them to 200, a hunter's short
 * trip. Since r22 (dev, 2026-10-26: "the cook might be leveling a touch too fast") only the cook's
 * recipe steps pay; its fuel, extract and stocking steps declare 0, which also replaces the formula.
 * <p>
 * The v1 and v2 parse paths run through the production loader. The two template expansions (cook
 * simple-furnace food, armorer) iterate item tags that a unit JVM leaves empty (see
 * {@code CookStarterRecipeLoaderTest}); they chain the same parse.
 */
class JobExperienceFieldTest {

    private static final String JOBS_PATH = "data/questown/questown_jobs/";
    private static final Gson GSON = new Gson();
    private static final Set<String> COOK_SUPPORT_STEPS = Set.of(
            "cook_fuel.json", "cook_extract.json", "cook_stock_fuel.json", "cook_stock_ingredients.json"
    );

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void v2Job_declaredExperienceReplacesTheFormula() {
        Work cook = load("cook_fish", readJson("cook_fish.json")).get(new JobID("cook", "fish"));

        Assertions.assertEquals(200, cook.experienceEarned(1));
    }

    @Test
    void declaredZero_earnsNothing_notTheFormula() {
        Work extract = load("cook_extract", readJson("cook_extract.json")).get(new JobID("cook", "extract"));

        Assertions.assertEquals(0, extract.experienceEarned(1));
    }

    @Test
    void v1Job_declaredExperienceReplacesTheFormula() {
        JsonObject stick = readJson("crafter_stick.json");
        stick.addProperty("experience", 7);

        Work work = load("crafter_stick", stick).get(new JobID("crafter", "stick"));

        Assertions.assertEquals(7, work.experienceEarned(251));
    }

    @Test
    void noField_keepsTheFormula() {
        Work work = load("crafter_stick", readJson("crafter_stick.json")).get(new JobID("crafter", "stick"));

        Assertions.assertEquals(251, work.experienceEarned(251));
    }

    @Test
    void cookRecipeStepsAndBakerJobsEarn200_cookSupportStepsNothing() throws IOException, URISyntaxException {
        Map<String, JsonElement> declared = new TreeMap<>();
        for (Path p : jobFiles()) {
            String name = p.getFileName().toString();
            if (!name.startsWith("cook_") && !name.startsWith("baker_")) {
                continue;
            }
            try (Reader r = Files.newBufferedReader(p, StandardCharsets.UTF_8)) {
                JsonObject job = GSON.fromJson(r, JsonObject.class);
                declared.put(name, job.get("experience"));
            }
        }
        Assertions.assertEquals(11, declared.size(), "cook and baker job files");
        declared.forEach((name, xp) -> {
            int want = COOK_SUPPORT_STEPS.contains(name) ? 0 : 200;
            Assertions.assertTrue(xp != null && xp.getAsInt() == want, name + " declares experience " + want + ", got " + xp);
        });
    }

    private static Map<JobID, Work> load(String name, JsonObject json) {
        ResourceJobLoader.ReloadListener listener = ResourceJobLoader.LISTENER;
        Map<ResourceLocation, JsonElement> files = new LinkedHashMap<>();
        files.put(new ResourceLocation("questown_jobs/" + name), json);
        listener.apply(files, null, InactiveProfiler.INSTANCE);
        return listener.getJobs();
    }

    private static List<Path> jobFiles() throws IOException, URISyntaxException {
        URL dir = JobExperienceFieldTest.class.getClassLoader().getResource("data/questown/questown_jobs");
        Assertions.assertNotNull(dir, "job data directory on the test classpath");
        try (Stream<Path> files = Files.list(Path.of(dir.toURI()))) {
            return files.filter(f -> f.toString().endsWith(".json")).toList();
        }
    }

    private static JsonObject readJson(String file) {
        try (InputStream is = JobExperienceFieldTest.class.getClassLoader().getResourceAsStream(JOBS_PATH + file)) {
            Assertions.assertNotNull(is, "resource not found: " + file);
            return GSON.fromJson(new InputStreamReader(is, StandardCharsets.UTF_8), JsonObject.class);
        } catch (IOException e) {
            throw new RuntimeException("Failed to read " + file, e);
        }
    }
}
