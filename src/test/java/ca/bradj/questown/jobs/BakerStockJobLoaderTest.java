package ca.bradj.questown.jobs;

import com.google.common.collect.ImmutableMap;
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

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Loads the two baker stock job files through the PRODUCTION loader
 * ({@link ResourceJobLoader.ReloadListener#apply}), not the MC-free
 * {@code TestJobLoader} that most job-data tests use.
 *
 * <p>Why this exists: {@code workFromJsonV2} requires {@code "block"} to be a JSON
 * object ({@code {"id": ...}}) and throws {@code "block must be an object"} on the v1
 * string form. The 2026-10-10 cook-shape migration (commit f2f049cc) shipped
 * {@code baker_stock_wheat.json} / {@code baker_stock_coal.json} at version 2 with the
 * v1 string form, so both jobs silently failed to load at server start (the failure is
 * logged and skipped unless {@code crash_on_invalid_jobs} is set). The MC-free
 * {@code TestJobLoader} never inspects the {@code block} field, so the
 * {@code JobIntegrationTest} coverage of these files stayed green.
 */
class BakerStockJobLoaderTest {

    private static final String JOBS_PATH = "data/questown/questown_jobs/";
    private static final Gson GSON = new Gson();

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void bakerStockJobs_parseThroughProductionLoader() {
        ResourceJobLoader.ReloadListener listener = ResourceJobLoader.LISTENER;

        Map<ResourceLocation, JsonElement> files = new LinkedHashMap<>();
        files.put(new ResourceLocation("questown_jobs/baker_stock_wheat"),
                readJson(JOBS_PATH + "baker_stock_wheat.json"));
        files.put(new ResourceLocation("questown_jobs/baker_stock_coal"),
                readJson(JOBS_PATH + "baker_stock_coal.json"));

        listener.apply(files, null, InactiveProfiler.INSTANCE);

        ImmutableMap<JobID, Work> jobs = listener.getJobs();
        Work wheat = jobs.get(new JobID("baker", "stock_wheat"));
        Work coal = jobs.get(new JobID("baker", "stock_coal"));
        Assertions.assertNotNull(wheat, "baker/stock_wheat must load via the production v2 path");
        Assertions.assertNotNull(coal, "baker/stock_coal must load via the production v2 path");

        // v2 fields survive the real parse
        Assertions.assertEquals(new JobID("baker", "extract"), wheat.parentID);
        Assertions.assertEquals(new JobID("baker", "fuel"), coal.parentID);
        Assertions.assertEquals(new ResourceLocation("questown:breadmaker_stocked"), wheat.baseRoom);
        Assertions.assertEquals(new ResourceLocation("questown:breadmaker_stocked"), coal.baseRoom);
        Assertions.assertTrue(wheat.getSpecialGlobalRules().contains(SpecialRules.ALWAYS_CONSIDER));
        Assertions.assertTrue(wheat.getSpecialGlobalRules().contains(SpecialRules.EXCLUDE_FROM_WARP));
        Assertions.assertTrue(coal.getSpecialGlobalRules().contains(SpecialRules.ALWAYS_CONSIDER));
        Assertions.assertTrue(coal.getSpecialGlobalRules().contains(SpecialRules.EXCLUDE_FROM_WARP));
    }

    private static JsonElement readJson(String resourcePath) {
        try (InputStream is = BakerStockJobLoaderTest.class.getClassLoader().getResourceAsStream(resourcePath)) {
            Assertions.assertNotNull(is, "resource not found: " + resourcePath);
            return GSON.fromJson(new InputStreamReader(is, StandardCharsets.UTF_8), JsonObject.class);
        } catch (java.io.IOException e) {
            throw new RuntimeException("Failed to read " + resourcePath, e);
        }
    }
}
