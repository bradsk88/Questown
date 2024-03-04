package ca.bradj.questown.town;

import ca.bradj.questown.core.Config;

public record VillagerStatsData(
        float fullnessPercent,
        float moodPercent
) {
    public static VillagerStatsData empty() {
        return new VillagerStatsData(0, Config.NEUTRAL_MOOD.get());
    }
}
