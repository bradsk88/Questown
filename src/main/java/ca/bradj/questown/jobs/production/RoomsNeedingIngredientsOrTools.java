package ca.bradj.questown.jobs.production;

import ca.bradj.questown.integration.minecraft.MCTownItem;
import ca.bradj.questown.logic.PredicateCollection;
import ca.bradj.roomrecipes.adapter.IRoomRecipeMatch;
import ca.bradj.roomrecipes.adapter.RoomRecipeMatch;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class RoomsNeedingIngredientsOrTools<ROOM, RECIPE> {
    private final ImmutableMap<Integer, Collection<IRoomRecipeMatch<ROOM, RECIPE, ?, ?>>> inner;

    public RoomsNeedingIngredientsOrTools(
            ImmutableMap<Integer, Collection<IRoomRecipeMatch<ROOM, RECIPE, ?, ?>>> kvImmutableMap
    ) {
        inner = kvImmutableMap;
    }

    public Map<Integer, Collection<RoomRecipeMatch<ROOM>>> get() {
        return null;
    }
    public Map<Integer, Collection<ROOM>> getRooms() {
        return null;
    }

    public ImmutableList<PredicateCollection<MCTownItem, ?>> cleanFns() {
        return null;
    }

    public Set<Integer> getNonEmptyStates() {
        return inner.entrySet()
                .stream()
                .filter(
                        v -> !v.getValue()
                               .isEmpty()
                )
                .map(Map.Entry::getKey)
                .collect(Collectors.toSet());
    }

    public RoomsNeedingIngredientsOrTools<ROOM, RECIPE> floor() {
        // If a single room needs supplies (for example) for BOTH states 0 and 1, it should only
        // show up as "needing" 0.
        Map<Integer, Collection<IRoomRecipeMatch<ROOM, RECIPE, ?, ?>>> b = new HashMap<>();
        inner.forEach((k, rooms) -> {
            ImmutableSet.Builder<IRoomRecipeMatch<ROOM, RECIPE, ?, ?>> allPrevRooms = ImmutableSet.builder();
            for (int i = 0; i < k; i++) {
                Collection<IRoomRecipeMatch<ROOM, RECIPE, ?, ?>> elements = b.get(i);
                if (elements == null) {
                    elements = ImmutableList.of();
                }
                allPrevRooms.addAll(elements);
            }
            ImmutableSet<IRoomRecipeMatch<ROOM, RECIPE, ?, ?>> prevRooms = allPrevRooms.build();
            ImmutableList.Builder<IRoomRecipeMatch<ROOM, RECIPE, ?, ?>> bld = ImmutableList.builder();
            rooms.forEach(room -> {
                if (prevRooms.contains(room)) {
                    return;
                }
                bld.add(room);
            });
            ImmutableList<IRoomRecipeMatch<ROOM, RECIPE, ?, ?>> build = bld.build();
            if (!build.isEmpty()) {
                b.put(k, build);
            }
        });
        return new RoomsNeedingIngredientsOrTools<>(
                ImmutableMap.copyOf(b)
        );
    }

    public boolean containsKey(Integer s) {
        return false;
    }

    public Collection<IRoomRecipeMatch<ROOM, RECIPE, ?, ?>> get(Integer s) {
        return null;
    }
}
