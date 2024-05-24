package ca.bradj.questown.jobs.production;

import ca.bradj.roomrecipes.core.Room;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

import java.util.Collection;
import java.util.Map;

public class TownNeedsMap<STATUS> {
    private static final TownNeedsMap NONE = new TownNeedsMap(ImmutableMap.of(), ImmutableMap.of());
    public final ImmutableMap<STATUS, ImmutableList<Room>> roomsWhereWorkCanBeDoneByEntity;
    public final ImmutableMap<STATUS, ImmutableList<Room>> roomsWhereSuppliesCanBeUsed;

    public TownNeedsMap(
            Map<STATUS, ImmutableList<Room>> roomsWhereWorkCanBeDoneByEntity,
            Map<STATUS, ImmutableList<Room>> roomsWhereSuppliesCanBeUsed
    ) {
        ImmutableMap.Builder<STATUS, ImmutableList<Room>> b1 = ImmutableMap.builder();
        roomsWhereSuppliesCanBeUsed.forEach((k, v) -> b1.put(k, ImmutableList.copyOf(v)));
        this.roomsWhereSuppliesCanBeUsed = b1.build();
        ImmutableMap.Builder<STATUS, ImmutableList<Room>> b2 = ImmutableMap.builder();
        roomsWhereWorkCanBeDoneByEntity.forEach((k, v) -> b2.put(k, ImmutableList.copyOf(v)));
        this.roomsWhereWorkCanBeDoneByEntity = b2.build();
    }

    public static <STATUS> TownNeedsMap<STATUS> NONE() {
        return (TownNeedsMap<STATUS>) NONE;
    }
}
