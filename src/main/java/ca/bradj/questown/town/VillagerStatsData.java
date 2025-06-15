package ca.bradj.questown.town;

import ca.bradj.questown.core.Config;

public record VillagerStatsData(float fullnessPercent, int experienceValue, int experienceTarget, float moodPercent,
                                float damageLevelPercent) {
    public static VillagerStatsData empty() {
        return new VillagerStatsData(0, 0, Config.EXPERIENCE_REQUIRED_AT_LEVEL_1.get(), Config.NEUTRAL_MOOD.get(), 0);
    }
}
