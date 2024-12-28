package ca.bradj.questown.town;

import com.google.common.collect.EvictingQueue;

import java.util.UUID;

@SuppressWarnings("UnstableApiUsage")
public class TownEconomics {

    record UnmetNeed(
            long tick,
            UUID villager,
            String request
    ) {
    }

    // TODO[ASAP]: Validate need record size
    private final EvictingQueue<UnmetNeed> unmetNeedsRecord = EvictingQueue.create(100);

    public void registerUnmetNeed(
            long tick,
            UUID villagerId,
            String requested
    ) {
        unmetNeedsRecord.add(new UnmetNeed(tick, villagerId, requested));
    }
}
