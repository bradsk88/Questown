package ca.bradj.questown.jobs.leaver;

public class RankBoost {
    private final float value;

    private RankBoost(float value) {
        this.value = value;
    }

    public static RankBoost SAME_AS_VANILLA_CHEST = new RankBoost(1f);
    public static RankBoost SLIGHTLY_PREFERRED = new RankBoost(2f);
    public static RankBoost VERY_PREFERRED = new RankBoost(3f);
    public static RankBoost EXTREMELY_PREFERRED = new RankBoost(4f);

    public float value() {
        return value;
    }
}
