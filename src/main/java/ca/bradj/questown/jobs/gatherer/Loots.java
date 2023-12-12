package ca.bradj.questown.jobs.gatherer;

import ca.bradj.questown.Questown;
import ca.bradj.questown.integration.minecraft.MCHeldItem;
import ca.bradj.questown.integration.minecraft.MCTownItem;
import ca.bradj.questown.items.GathererMap;
import ca.bradj.questown.jobs.Journal;
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
import java.util.Collections;
import java.util.List;

public class Loots {

    @NotNull
    static List<ItemStack> getFromLootTables(
            ServerLevel level,
            Journal<?, MCHeldItem, ?> journal,
            GathererTools.LootTablePrefix ltPrefix,
            GathererTools.LootTablePath defaultLT
    ) {
        @Nullable ResourceLocation biome = GathererMap.computeBiome(journal.getItems());
        if (biome == null) {
            biome = new ResourceLocation("forest"); // TODO: Something better?
        }
        String id = String.format("%s/%s/%s", ltPrefix.value(), biome.getNamespace(), biome.getPath());
        ResourceLocation rl = new ResourceLocation(Questown.MODID, id);
        LootTables tables = level.getServer().getLootTables();
        if (!tables.getIds().contains(rl)) {
            rl = new ResourceLocation(Questown.MODID, defaultLT.path());
        }
        LootTable lootTable = tables.get(rl);
        List<MCTownItem> loot = Loots.loadFromTables(level, lootTable, 3, journal.getCapacity());
        return loot.stream().map(MCTownItem::toItemStack).toList();
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
        int max = Math.min(minItems, level.random.nextInt(maxItems) + 1);
        while (rItems.size() < max) {
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
