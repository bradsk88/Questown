package ca.bradj.questown.town;

import ca.bradj.questown.QT;
import ca.bradj.questown.core.init.items.ItemsInit;
import ca.bradj.questown.jobs.HeldItem;
import ca.bradj.questown.jobs.Item;
import ca.bradj.questown.jobs.gatherer.GathererTools;
import ca.bradj.questown.town.interfaces.KnowledgeHolder;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import org.apache.commons.lang3.function.TriFunction;

import java.util.*;
import java.util.function.Function;

public class KnowledgeStore<BIOME, ITEM_IN extends HeldItem<ITEM_IN, ?>, ITEM_OUT extends Item<?>> implements KnowledgeHolder<BIOME, ITEM_IN, ITEM_OUT> {


    private final ImmutableSet<ITEM_OUT> baseKnowledge;
    private final Function<ITEM_IN, ITEM_OUT> stripper;
    private final TriFunction<GathererTools.LootTablePrefix, BIOME, ITEM_OUT, ITEM_IN> unstripper;
    private final Function<String, BIOME> biomeFactory;

    public KnowledgeStore(
            ImmutableSet<ITEM_OUT> baseKnowledge,
            Function<ITEM_IN, ITEM_OUT> stripper,
            TriFunction<GathererTools.LootTablePrefix, BIOME, ITEM_OUT, ITEM_IN> unstripper,
            Function<String, BIOME> biomeFactory
    ) {
        this.baseKnowledge = baseKnowledge;
        this.stripper = stripper;
        this.unstripper = unstripper;
        this.biomeFactory = biomeFactory;
    }

    private final Map<BIOME, Map<GathererTools.LootTablePrefix, ImmutableSet<ITEM_OUT>>> knownGatherResults = new HashMap<>();

    @Override
    public ImmutableSet<ITEM_OUT> getAllKnownGatherResults(
            Collection<BIOME> mapBiomes, GathererTools.LootTablePrefix ltPrefix
    ) {
        ImmutableSet.Builder<ITEM_OUT> b = ImmutableSet.builder();
        b.addAll(baseKnowledge);

        ImmutableList.Builder<Map<GathererTools.LootTablePrefix, ImmutableSet<ITEM_OUT>>> vb = ImmutableList.builder();
        knownGatherResults.forEach((k, v) -> {
            if (mapBiomes.contains(k)) {
                vb.add(v);
            }
        });

        vb.build()
                .stream()
                .flatMap(v -> v.getOrDefault(ltPrefix, ImmutableSet.of()).stream())
                .forEach(b::add);
        return b.build();
    }

    public ImmutableSet<ITEM_IN> getAllKnownGatherResults() {
        ImmutableSet.Builder<ITEM_IN> b = ImmutableSet.builder();
        baseKnowledge.forEach(v -> b.add(unstripper.apply(GathererTools.NO_TOOL_TABLE_PREFIX, null, v)));
        for (Map.Entry<BIOME, Map<GathererTools.LootTablePrefix, ImmutableSet<ITEM_OUT>>> i : knownGatherResults.entrySet()) {
            for (Map.Entry<GathererTools.LootTablePrefix, ImmutableSet<ITEM_OUT>> j : i.getValue().entrySet()) {
                for (ITEM_OUT k : j.getValue()) {
                    b.add(unstripper.apply(j.getKey(), i.getKey(), k));
                }
            }
        }
        return b.build();
    }

    @Override
    public void registerFoundLoots(Collection<ITEM_IN> items) {
        items.forEach(item -> {
            if (item.foundInBiome() == null) {
                return;
            }
            String o = item.foundInBiome();
            BIOME bId = biomeFactory.apply(o);
            Map<GathererTools.LootTablePrefix, ImmutableSet<ITEM_OUT>> biome = knownGatherResults.get(bId);
            if (biome == null) {
                biome = new HashMap<>();
            }
            String lootPrefix = item.acquiredViaLootTablePrefix();
            if (lootPrefix == null) {
                if (ItemsInit.GATHERER_MAP.get().equals(item)) {
                    return;
                }
                QT.FLAG_LOGGER.error("Found item has no loot table prefix");
                return;
            }
            GathererTools.LootTablePrefix ltp = new GathererTools.LootTablePrefix(lootPrefix);
            ImmutableSet<ITEM_OUT> knownBiomeItems = biome.get(ltp);
            if (knownBiomeItems == null) {
                knownBiomeItems = ImmutableSet.of();
            }
            Set<ITEM_OUT> known = new HashSet<>(knownBiomeItems);
            int sizeBefore = known.size();
            ITEM_OUT stripped = stripper.apply(item);
            known.add(stripped);
            if (sizeBefore != known.size()) {
                QT.FLAG_LOGGER.debug("New item recorded as 'known': {} in biome {}", stripped.getShortName(), bId);
            }
            biome.put(ltp, ImmutableSet.copyOf(known));
            knownGatherResults.put(bId, biome);
        });

    }
}
