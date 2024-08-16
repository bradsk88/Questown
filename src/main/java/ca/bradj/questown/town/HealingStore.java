package ca.bradj.questown.town;

import ca.bradj.questown.core.UtilClean;
import com.google.common.collect.ImmutableMap;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

// TODO: Optimize Performance
//  We check every healing spot every tick. Distribute across many ticks.
public class HealingStore<POS> {

    private final Map<POS, Double> healingBedsGroundTruth = new HashMap<>();
    private final Map<POS, Map<String, TemporaryBoost>> boosts = new HashMap<>();
    private final Map<POS, Double> healingBedsBoosted = new HashMap<>();

    public ImmutableMap<POS, Double> getAll() {
        return ImmutableMap.copyOf(healingBedsBoosted);
    }

    public void tick() {
        for (Map.Entry<POS, Map<String, TemporaryBoost>> boostMap : boosts.entrySet()) {
            tickForPosition(boostMap.getKey(), boostMap.getValue());
        }
    }

    private void tickForPosition(POS pos, Map<String, TemporaryBoost> boosts) {
        Double value = UtilClean.getOrDefault(healingBedsGroundTruth, pos, 1.0);
        for (String boostKey : boosts.keySet()) {
            TemporaryBoost ticked = boosts.computeIfPresent(
                    boostKey,
                    (k, v) -> v.ticked()
            );
            if (Objects.requireNonNull(ticked).isDone()) {
                boosts.remove(boostKey);
                continue;
            }
            value = value * ticked.factor();
        }
        healingBedsBoosted.put(pos, value);
    }

    public void addBoost(
            POS p,
            String uniquenessKey,
            TemporaryBoost boost
    ) {
        UtilClean.putOrInitialize(boosts, p, uniquenessKey, boost.rewind());
        tickForPosition(p, boosts.get(p));
    }

    public Double getHealFactor(POS p) {
        return UtilClean.getOrDefault(healingBedsBoosted, p, 1.0);
    }

    protected void putGroundTruth(
            POS p,
            Double factor
    ) {
        healingBedsGroundTruth.put(p, factor);
    }
}
