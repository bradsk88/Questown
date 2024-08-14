package ca.bradj.questown.town;

import ca.bradj.questown.core.Config;

public record VillagerStatsData(
        float fullnessPercent,
        float moodPercent,
        float damageLevelPercent
) {
    public static VillagerStatsData empty() {
        return new VillagerStatsData(0, Config.NEUTRAL_MOOD.get(), 0);
    }
}
