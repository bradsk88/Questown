package ca.bradj.questown.town;

import ca.bradj.questown.core.UtilClean;
import ca.bradj.questown.gui.ItemEconomicsData;
import ca.bradj.questown.town.quests.RoomNeed;
import com.google.common.collect.EvictingQueue;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

@SuppressWarnings("UnstableApiUsage")
public class NoMCEconomics {

    // TODO[ASAP]: Validate need record size
    private final EvictingQueue<UnmetNeed> unmetNeedsRecord = EvictingQueue.create(100);
    private final EvictingQueue<UnmetNeed> unmetRoomsRecord = EvictingQueue.create(100);
    private boolean needAggregate;
    private Map<UUID, ImmutableList<ItemEconomicsData>> aggregated = ImmutableMap.of();
    private ImmutableList<ItemEconomicsData> aggregatedAll = ImmutableList.of();
    private ImmutableList<RoomNeed<String>> roomsAll = ImmutableList.of();

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

    public static ImmutableList<RoomNeed<String>> aggregateRooms(Collection<UnmetNeed> data) {
        Map<UUID, Map<String, Integer>> map = generateCounts(data);
        Map<String, RoomNeed<String>> needsOut = new HashMap<>();
        for (Map.Entry<UUID, Map<String, Integer>> villagerMap : map.entrySet()) {
            for (Map.Entry<String, Integer> reqMap : villagerMap.getValue().entrySet()) {
                String k = reqMap.getKey();
                RoomNeed<String> current = UtilClean.getOrDefault(needsOut, k, new RoomNeed<>(k, 0, 0));
                RoomNeed<String> newVal = current
                        .withVillagers(current.villagersWhoNeed() + 1)
                        .withTimes(current.timesNeeded() + reqMap.getValue());
                needsOut.put(k, newVal);
            }
        }
        return ImmutableList.copyOf(needsOut.values());
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

    public boolean update() {
        if (!needAggregate) {
            return false;
        }
        this.aggregated = aggregateForUI(unmetNeedsRecord);
        this.aggregatedAll = buildOverallData();
        this.roomsAll = aggregateRooms(unmetRoomsRecord);
        return true;
    }

    private @Nullable ImmutableList<ItemEconomicsData> buildOverallData() {
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
        ImmutableList<ItemEconomicsData> aggregatedAll1 = x.get(standIn);
        return aggregatedAll1;
    }

    public void registerUnmetNeed(
            long tick,
            UUID villagerId,
            String requested
    ) {
        unmetNeedsRecord.add(new UnmetNeed(tick, villagerId, requested));
        this.needAggregate = true;
    }

    public void registerUnmetRoom(
            long tick,
            UUID villagerId,
            String requested
    ) {
        unmetRoomsRecord.add(new UnmetNeed(tick, villagerId, requested));
        this.needAggregate = true;
    }

    public ImmutableList<ItemEconomicsData> getAggregatedItems(@Nullable UUID villagerId) {
        return getAggregated(aggregatedAll, aggregated, villagerId);
    }

    public Collection<RoomNeed<String>> getAggregatedRooms() {
        return roomsAll;
    }

    public interface Needable {
        int timesNeeded();
    }

    private static <S extends Needable> ImmutableList<S> getAggregated(
            List<S> all,
            Map<UUID, ? extends List<S>> allSplitByVillager,
            @Nullable UUID villagerId
    ) {
        List<S> l = all;
        if (villagerId != null) {
            l = UtilClean.getOrDefaultCollection(
                    allSplitByVillager,
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
