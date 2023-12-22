package ca.bradj.questown.town;

import ca.bradj.questown.jobs.HeldItem;

public interface VillagerDataCollectionHolder<H extends HeldItem<H, ?>> {
    TownState.VillagerData<H> getVillager(int villagerIndex);
}
