package ca.bradj.questown.mc;

import ca.bradj.questown.QT;
import ca.bradj.questown.Questown;
import ca.bradj.questown.jobs.gatherer.GathererTools;
import ca.bradj.questown.jobs.gatherer.LootTableDrops;
import ca.bradj.questown.jobs.gatherer.LootTableLikelihood;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;

import java.io.Reader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Per-prefix cache of {@link LootTableDrops} over the server's job loot tables
 * ({@code data/questown/loot_tables/<prefix>/**}; the folder is {@code loot_tables} on 1.20.6).
 * Rebuilt when the server's resource manager changes (a datapack reload replaces it).
 */
public final class LootTableDropsIndex {

    private static ResourceManager indexedManager;
    private static final Map<GathererTools.LootTablePrefix, ImmutableSet<String>> DROPS = new HashMap<>();

    private LootTableDropsIndex() {
    }

    public static synchronized ImmutableSet<String> forPrefix(
            ResourceManager manager,
            GathererTools.LootTablePrefix prefix
    ) {
        if (manager != indexedManager) {
            DROPS.clear();
            LIKELY.clear();
            indexedManager = manager;
        }
        return DROPS.computeIfAbsent(prefix, p -> read(manager, p));
    }

    private static ImmutableSet<String> read(
            ResourceManager manager,
            GathererTools.LootTablePrefix prefix
    ) {
        Map<ResourceLocation, Resource> found = manager.listResources(
                "loot_tables/" + prefix.value(),
                rl -> Questown.MODID.equals(rl.getNamespace()) && rl.getPath().endsWith(".json")
        );
        List<JsonElement> tables = new ArrayList<>();
        for (Map.Entry<ResourceLocation, Resource> e : found.entrySet()) {
            try (Reader r = e.getValue().openAsReader()) {
                tables.add(JsonParser.parseReader(r));
            } catch (Exception ex) {
                QT.JOB_LOGGER.error("Could not read loot table {} for its drops", e.getKey(), ex);
            }
        }
        return LootTableDrops.itemIds(tables);
    }

    private static final Map<String, ImmutableList<String>> LIKELY = new HashMap<>();

    /**
     * The item ids of one questown loot table ({@code data/questown/loot_tables/<path>.json}), most
     * likely first ({@link LootTableLikelihood}); empty when the table cannot be read.
     */
    public static synchronized ImmutableList<String> mostLikely(
            ResourceManager manager,
            String path
    ) {
        if (manager != indexedManager) {
            DROPS.clear();
            LIKELY.clear();
            indexedManager = manager;
        }
        return LIKELY.computeIfAbsent(path, p -> readLikely(manager, p));
    }

    private static ImmutableList<String> readLikely(
            ResourceManager manager,
            String path
    ) {
        ResourceLocation id = new ResourceLocation(Questown.MODID, "loot_tables/" + path + ".json");
        Optional<Resource> res = manager.getResource(id);
        if (res.isEmpty()) {
            QT.JOB_LOGGER.error("No loot table {} to rank its drops", id);
            return ImmutableList.of();
        }
        try (Reader r = res.get().openAsReader()) {
            return LootTableLikelihood.itemIdsByLikelihood(JsonParser.parseReader(r));
        } catch (Exception ex) {
            QT.JOB_LOGGER.error("Could not read loot table {} to rank its drops", id, ex);
            return ImmutableList.of();
        }
    }
}
