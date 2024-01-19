package ca.bradj.questown.town;

public record VillagerStatsData(
        float fullnessPercent
) {
    public static VillagerStatsData empty() {
        return new VillagerStatsData(0);
    }
}
