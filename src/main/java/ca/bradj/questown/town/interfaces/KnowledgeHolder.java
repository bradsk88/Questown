package ca.bradj.questown.town.interfaces;

import ca.bradj.questown.jobs.gatherer.GathererTools;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;

import java.util.Collection;

public interface KnowledgeHolder<BIOME, ITEM_IN, ITEM_OUT> {
    ImmutableSet<ITEM_OUT> getAllKnownGatherResults(
            Collection<BIOME> mapBiomes, GathererTools.LootTablePrefix ltPrefix
    );

    void registerFoundLoots(ImmutableList<ITEM_IN> items);
}
