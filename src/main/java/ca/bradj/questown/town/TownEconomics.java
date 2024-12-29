package ca.bradj.questown.town;

import ca.bradj.questown.core.UtilClean;
import ca.bradj.questown.gui.ItemEconomicsData;
import com.google.common.collect.EvictingQueue;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@SuppressWarnings("UnstableApiUsage")
public class TownEconomics {

    // TODO[ASAP]: Validate need record size
    private final EvictingQueue<UnmetNeed> unmetNeedsRecord = EvictingQueue.create(100);
    private boolean needAggregate;
    private Map<UUID, ImmutableList<ItemEconomicsData>> aggregated = ImmutableMap.of();
    private ImmutableList<ItemEconomicsData> aggregatedAll = ImmutableList.of();

    public static ImmutableMap<UUID, ImmutableList<ItemEconomicsData>> aggregateForUI(
            Collection<UnmetNeed> unmetNeedsRecord
    ) {
        Map<UUID, Map<String, Integer>> map = generateCounts(unmetNeedsRecord);
        return aggregateCounts(map);
    }

    private static @NotNull ImmutableMap<UUID, ImmutableList<ItemEconomicsData>> aggregateCounts(Map<UUID, Map<String, Integer>> map) {
        ImmutableMap.Builder<UUID, ImmutableList<ItemEconomicsData>> b = ImmutableMap.builder();
        for (Map.Entry<UUID, Map<String, Integer>> villagerMap : map.entrySet()) {
            ImmutableList.Builder<ItemEconomicsData> b2 = ImmutableList.builder();
            for (Map.Entry<String, Integer> reqMap : villagerMap.getValue().entrySet()) {
                b2.add(new ItemEconomicsData(reqMap.getKey(), reqMap.getValue()));
            }
            b.put(villagerMap.getKey(), b2.build());
        }
        return b.build();
    }

    private static @NotNull Map<UUID, Map<String, Integer>> generateCounts(Collection<UnmetNeed> unmetNeedsRecord) {
        Map<UUID, Map<String, Integer>> map = new HashMap<>();
        for (UnmetNeed unmetNeed : unmetNeedsRecord) {
            map.compute(
                    unmetNeed.villager(), (u, m) -> {
                        if (m == null) {
                            HashMap<String, Integer> om = new HashMap<>();
                            om.put(unmetNeed.request(), 1);
                            return om;
                        }
                        m.compute(unmetNeed.request(), (k, v) -> v == null ? 1 : v + 1);
                        return m;
                    }
            );
        }
        return map;
    }

    public void tick() {
        if (!needAggregate) {
            return;
        }
        this.aggregated = aggregateForUI(unmetNeedsRecord);
        HashMap<String, Integer> b = new HashMap<>();
        for (ImmutableList<ItemEconomicsData> v : aggregated.values()) {
            for (ItemEconomicsData i : v) {
                b.compute(
                        i.ingredientKey(),
                        (k, vv) -> vv == null ? i.timesNeeded() : vv + i.timesNeeded()
                );
            }
        }
        UUID standIn = UUID.randomUUID();
        Map<UUID, ImmutableList<ItemEconomicsData>> x = aggregateCounts(
                ImmutableMap.of(standIn, b)
        );
        this.aggregatedAll = x.get(standIn);
    }

    public void registerUnmetNeed(
            long tick,
            UUID villagerId,
            String requested
    ) {
        unmetNeedsRecord.add(new UnmetNeed(tick, villagerId, requested));
        this.needAggregate = true;
    }

    public ImmutableList<ItemEconomicsData> getAggregated(UUID villagerId) {
        ImmutableList<ItemEconomicsData> l = aggregatedAll;
        if (villagerId != null) {
            l = UtilClean.getOrDefaultCollection(
                    aggregated,
                    villagerId,
                    ImmutableList.of()
            );
        }
        return ImmutableList.copyOf(
                l.stream().sorted((a, b) -> Integer.compare(b.timesNeeded(), a.timesNeeded())).toList()
        );
    }

    public record UnmetNeed(
            long tick,
            UUID villager,
            String request
    ) {
    }
}
