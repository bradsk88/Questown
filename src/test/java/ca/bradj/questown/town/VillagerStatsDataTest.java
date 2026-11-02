package ca.bradj.questown.town;

import com.google.common.collect.ImmutableList;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class VillagerStatsDataTest {

    @Test
    public void summary_namesEveryField() {
        VillagerStatsData stats = new VillagerStatsData(
                0.52f, 3, 12, 0.8f, 0.0f, 4, ImmutableList.of("ate_without_table")
        );

        Assertions.assertEquals(
                "Lvl 4, xp 3/12, fullness 52%, mood 80%, damage 0%, effects [ate_without_table]",
                stats.summary()
        );
    }

    @Test
    public void summary_noEffects() {
        VillagerStatsData stats = new VillagerStatsData(1.0f, 0, 10, 0.5f, 0.25f, 1, ImmutableList.of());

        Assertions.assertEquals(
                "Lvl 1, xp 0/10, fullness 100%, mood 50%, damage 25%, effects []",
                stats.summary()
        );
    }
}
