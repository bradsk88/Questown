package ca.bradj.questown.mc;

import ca.bradj.questown.QT;
import ca.bradj.questown.Questown;
import ca.bradj.questown.jobs.gatherer.GathererTools;
import ca.bradj.questown.jobs.gatherer.LootTableDrops;
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
}
