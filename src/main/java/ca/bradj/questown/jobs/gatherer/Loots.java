package ca.bradj.questown.jobs.gatherer;

import ca.bradj.questown.QT;
import ca.bradj.questown.Questown;
import ca.bradj.questown.core.Config;
import ca.bradj.questown.integration.minecraft.MCHeldItem;
import ca.bradj.questown.integration.minecraft.MCTownItem;
import ca.bradj.questown.items.GathererMap;
import com.google.common.collect.ImmutableList;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.LootTables;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

public class Loots {

    public static ResourceLocation fallbackBiome = new ResourceLocation("forest"); // TODO: Something better?

    @NotNull
    static List<MCHeldItem> getFromLootTables(
            ServerLevel level,
            Collection<MCHeldItem> items,
            GathererTools.LootTableParameters lt
    ) {
        final ResourceLocation biome = finalizeBiome(items);
        int maxAmount = items.size();
        return getFromLootTables(level, maxAmount / 2, maxAmount, lt, biome);
    }

    @NotNull
    static List<MCHeldItem> getFromLootTables(
            ServerLevel level,
            int maxAmount,
            GathererTools.LootTableParameters lt,
            ResourceLocation biome
    ) {
        return getFromLootTables(level, maxAmount / 2, maxAmount, lt, biome);
    }

    @NotNull
    static List<MCHeldItem> getFromLootTables(
            ServerLevel level,
            int minAmount, int maxAmount,
            GathererTools.LootTableParameters lt,
            ResourceLocation biome
    ) {
        String id = String.format("%s/%s/%s", lt.prefix().value(), biome.getNamespace(), biome.getPath());
        ResourceLocation rl = new ResourceLocation(Questown.MODID, id);
        LootTables tables = level.getServer().getLootTables();
        if (!tables.getIds().contains(rl)) {
            QT.JOB_LOGGER.warn("No loot table found for {}. Using fallback {}", id, lt.fallback());
            rl = new ResourceLocation(Questown.MODID, lt.fallback().path());
        }
        if (!tables.getIds().contains(rl)) {
            throw new IllegalStateException(
                    String.format(
                            "No loot table found for %s or fallback %s",
                            id, lt.fallback()
                    )
            );
        }
        LootTable lootTable = tables.get(rl);
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
    private static List<MCTownItem> loadFromTables(
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

        ArrayList<ItemStack> rItems = new ArrayList<>();
        int max = minItems + (level.random.nextInt(maxItems - minItems + 1));
        for (int i = 0; i < Config.BASE_MAX_LOOP.get(); i++) {
            if (rItems.size() >= max) {
                break;
            }
            rItems.addAll(lootTable.getRandomItems(lc));
        }
        Collections.shuffle(rItems);
        int subLen = Math.min(rItems.size(), maxItems);
        List<MCTownItem> list = rItems.stream()
                .filter(v -> !v.isEmpty())
                .map(MCTownItem::fromMCItemStack)
                .toList()
                .subList(0, subLen);
        return list;
    }
}
