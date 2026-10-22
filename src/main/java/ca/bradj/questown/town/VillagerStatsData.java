package ca.bradj.questown.town;

import ca.bradj.questown.core.Config;
import com.google.common.collect.ImmutableList;

/**
 * @param level       the townie's villager level (stats page "Lvl N")
 * @param moodEffects active mood effect ids ({@code MoodEffects}), so the stats page can say why the
 *                    mood is low ("Didn't sleep"); 2026-10-11 playtest
 */
public record VillagerStatsData(float fullnessPercent, int experienceValue, int experienceTarget, float moodPercent,
                                float damageLevelPercent, int level, ImmutableList<String> moodEffects) {
    public static VillagerStatsData empty() {
        return new VillagerStatsData(
                0, 0, Config.EXPERIENCE_REQUIRED_AT_LEVEL_1.get(), Config.NEUTRAL_MOOD.get(), 0, 1, ImmutableList.of()
        );
    }
}
