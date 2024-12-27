package ca.bradj.questown.gui;

import com.google.common.collect.ImmutableList;

public record VillagerEconomicsData(
        ImmutableList<ItemEconomicsData> items
) {
    public static VillagerEconomicsData none() {
        return new VillagerEconomicsData(
                ImmutableList.of()
        );
    }
}
