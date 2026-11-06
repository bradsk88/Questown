package ca.bradj.questown.jobs.gatherer;

import ca.bradj.questown.QT;
import ca.bradj.questown.Questown;
import ca.bradj.questown.core.Config;
import ca.bradj.questown.integration.minecraft.MCHeldItem;
import ca.bradj.questown.integration.minecraft.MCTownItem;
import ca.bradj.questown.items.GathererMap;
import ca.bradj.questown.mc.Compat;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.Reader;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static ca.bradj.questown.jobs.gatherer.GathererTools.NO_TOOL_LOOT_TABLE_DEFAULT;

public class Loots {

    public static ResourceLocation fallbackBiome = new ResourceLocation("forest"); // TODO: Something better?

    @NotNull
    public static List<MCHeldItem> getFromLootTables(
            ServerLevel level,
            Collection<MCHeldItem> items,
            GathererTools.LootTableParameters lt
    ) {
        final ResourceLocation biome = finalizeBiome(items);
        int amount = items.size();
        return getFromLootTables(level, amount, amount, lt, biome);
    }

    @NotNull
    public static List<MCHeldItem> getFromLootTables(
            ServerLevel level,
            Collection<MCHeldItem> items,
            int maxAmount,
            GathererTools.LootTableParameters lt
    ) {
        return getFromLootTables(level, maxAmount, maxAmount, lt, finalizeBiome(items));
    }

    @NotNull
    public static List<MCHeldItem> getFromLootTables(
            ServerLevel level,
            int maxAmount,
            GathererTools.LootTableParameters lt,
            ResourceLocation biome
    ) {
        return getFromLootTables(level, maxAmount, maxAmount, lt, biome);
    }

    @NotNull
    public static List<MCHeldItem> getFromLootTables(
            ServerLevel level,
            int minAmount, int maxAmount,
            GathererTools.LootTableParameters lt,
            ResourceLocation biome
    ) {
        String id = String.format("%s/%s/%s", lt.prefix().value(), biome.getNamespace(), biome.getPath());
        ResourceLocation rl = new ResourceLocation(Questown.MODID, id);
        if (!Compat.hasLootTable(level, rl)) {
            QT.JOB_LOGGER.warn("No loot table found for {}. Using fallback {}", id, lt.fallback());
            rl = new ResourceLocation(Questown.MODID, lt.fallback().path());
        }
        if (!Compat.hasLootTable(level, rl)) {
            QT.JOB_LOGGER.warn("No loot table found for {}. Using fallback {}", id, lt.fallback());
            rl = new ResourceLocation(Questown.MODID, NO_TOOL_LOOT_TABLE_DEFAULT.path());
        }
        if (!Compat.hasLootTable(level, rl)) {
            throw new IllegalStateException(
                    String.format(
                            "No loot table found for %s or fallback %s",
                            id, lt.fallback()
                    )
            );
        }
        return getFromKnownTable(level, minAmount, maxAmount, lt, biome, rl);
    }

    @NotNull
    public static List<MCHeldItem> getFromKnownTable(
            ServerLevel level, int minAmount, int maxAmount,
            GathererTools.LootTableParameters lt,
            ResourceLocation biome, ResourceLocation rl
    ) {
        LootTable lootTable = Compat.lootTable(level, rl);
        List<MCTownItem> loot = Loots.loadFromTables(level, lootTable, minAmount, maxAmount);
        return loot.stream().map(v -> MCHeldItem.fromLootTable(v, lt.prefix(), biome)).toList();
    }

    @NotNull
    private static ResourceLocation finalizeBiome(Collection<MCHeldItem> journal) {
        @Nullable ResourceLocation biome = GathererMap.computeBiome(journal);
        if (biome == null) {
            biome = fallbackBiome;
        }
        return biome;
    }

    @NotNull
    public static List<MCTownItem> loadFromTables(
            ServerLevel level,
            LootTable lootTable,
            int minItems,
            int maxItems
    ) {
        if (maxItems <= 0) {
            return ImmutableList.of();
        }

        LootContext.Builder lcb = new LootContext.Builder(level);
        LootContext lc = lcb.create(LootContextParamSets.EMPTY);

        int max = minItems + (level.random.nextInt(maxItems - minItems + 1));
        // Empty rolls don't count: a trip brings back an item per attempt.
        List<ItemStack> rItems = new ArrayList<>(LootRolls.roll(
                max, Compat.configGet(Config.BASE_MAX_LOOP).get(), () -> lootTable.getRandomItems(lc), ItemStack::isEmpty
        ));
        Collections.shuffle(rItems);
        return rItems.stream()
                .limit(max)
                .map(MCTownItem::fromMCItemStack)
                .toList();
    }

    // Read from the loot table files, cached until a data reload.
    private static ResourceManager previewedManager;
    private static final Map<GathererTools.LootTablePrefix, ImmutableSet<String>> PREVIEWS = new HashMap<>();
    private static final Map<String, ImmutableList<String>> MOST_LIKELY = new HashMap<>();

    /** Every item the job loot tables under {@code <prefix>} can drop. */
    public static synchronized ImmutableSet<String> previewForPrefix(
            ResourceManager manager,
            GathererTools.LootTablePrefix prefix
    ) {
        resetPreviewsIfReloaded(manager);
        return PREVIEWS.computeIfAbsent(prefix, p -> readDrops(manager, p));
    }

    /** One job loot table's items, most likely first; empty when it can't be read. */
    public static synchronized ImmutableList<String> getMostLikelyItems(
            ResourceManager manager,
            String table
    ) {
        resetPreviewsIfReloaded(manager);
        return MOST_LIKELY.computeIfAbsent(table, t -> readMostLikely(manager, t));
    }

    private static void resetPreviewsIfReloaded(ResourceManager manager) {
        if (manager != previewedManager) {
            PREVIEWS.clear();
            MOST_LIKELY.clear();
            previewedManager = manager;
        }
    }

    private static ImmutableSet<String> readDrops(
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

    private static ImmutableList<String> readMostLikely(
            ResourceManager manager,
            String table
    ) {
        ResourceLocation id = new ResourceLocation(Questown.MODID, "loot_tables/" + table + ".json");
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
