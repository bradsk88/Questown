package ca.bradj.questown.jobs;

import com.google.common.collect.ImmutableMap;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.SharedConstants;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.util.profiling.InactiveProfiler;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Loads the baker stock jobs through the real loader, which requires {@code "block"} to be an object. */
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

        Assertions.assertEquals(new JobID("baker", "extract"), wheat.parentID);
        Assertions.assertEquals(new JobID("baker", "fuel"), coal.parentID);
        Assertions.assertEquals(new ResourceLocation("questown:breadmaker_stocked"), wheat.baseRoom);
        Assertions.assertEquals(new ResourceLocation("questown:breadmaker_stocked"), coal.baseRoom);
        Assertions.assertTrue(wheat.getSpecialGlobalRules().contains(SpecialRules.ALWAYS_CONSIDER));
        Assertions.assertTrue(wheat.getSpecialGlobalRules().contains(SpecialRules.EXCLUDE_FROM_WARP));
        Assertions.assertTrue(coal.getSpecialGlobalRules().contains(SpecialRules.ALWAYS_CONSIDER));
        Assertions.assertTrue(coal.getSpecialGlobalRules().contains(SpecialRules.EXCLUDE_FROM_WARP));
    }

    /** A stock job's {@code initial_request} names the chain's product (bread), since it is read as what the job makes. */
    @Test
    void bakerStockJobs_doNotDeclareTheirInputsAsProducts() {
        ResourceJobLoader.ReloadListener listener = ResourceJobLoader.LISTENER;

        Map<ResourceLocation, JsonElement> files = new LinkedHashMap<>();
        files.put(new ResourceLocation("questown_jobs/baker_stock_wheat"),
                readJson(JOBS_PATH + "baker_stock_wheat.json"));
        files.put(new ResourceLocation("questown_jobs/baker_stock_coal"),
                readJson(JOBS_PATH + "baker_stock_coal.json"));
        listener.apply(files, null, InactiveProfiler.INSTANCE);

        List<ServerJobsRegistry.ProducerDeclaration> declared = new ArrayList<>();
        listener.getJobs().forEach((id, work) -> declared.add(
                new ServerJobsRegistry.ProducerDeclaration(id, work.initialRequest.apply(null))
        ));

        Assertions.assertEquals(Set.of(), ServerJobsRegistry.rootsThatCanMake(declared, Ingredient.of(Items.COAL)));
        Assertions.assertEquals(Set.of(), ServerJobsRegistry.rootsThatCanMake(declared, Ingredient.of(Items.WHEAT)));
        Assertions.assertEquals(Set.of("baker"), ServerJobsRegistry.rootsThatCanMake(declared, Ingredient.of(Items.BREAD)));
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
