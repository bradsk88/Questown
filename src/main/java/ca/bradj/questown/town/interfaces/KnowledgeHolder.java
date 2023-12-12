package ca.bradj.questown.town.interfaces;

import ca.bradj.questown.jobs.gatherer.GathererTools;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;

public interface KnowledgeHolder<ITEM_IN, ITEM_OUT> {
    ImmutableSet<ITEM_OUT> getAllKnownGatherResults(
            GathererTools.LootTablePrefix ltPrefix
    );

    void registerFoundLoots(ImmutableList<ITEM_IN> items);
}
