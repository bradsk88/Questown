package ca.bradj.questown.jobs.production;

import ca.bradj.questown.integration.minecraft.MCTownItem;
import ca.bradj.questown.logic.PredicateCollection;
import ca.bradj.roomrecipes.adapter.IRoomRecipeMatch;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class RoomsNeedingIngredientsOrTools<ROOM, RECIPE, POS> {
    private final ImmutableMap<Integer, Collection<IRoomRecipeMatch<ROOM, RECIPE, POS, ?>>> inner;

    public RoomsNeedingIngredientsOrTools(
            Map<Integer, ? extends Collection<? extends IRoomRecipeMatch<ROOM, RECIPE, POS, ?>>> kvImmutableMap
    ) {
        ImmutableMap.Builder<Integer, Collection<IRoomRecipeMatch<ROOM, RECIPE, POS, ?>>> builder = ImmutableMap.builder();
        kvImmutableMap.forEach((k, v) -> {
            ImmutableList.Builder<IRoomRecipeMatch<ROOM, RECIPE, POS, ?>> b = ImmutableList.builder();
            v.forEach(b::add);
            builder.put(k, b.build());
        });
        inner = builder.build();
    }

    public Map<Integer, Collection<IRoomRecipeMatch<ROOM, RECIPE, POS, ?>>> get() {
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

    public RoomsNeedingIngredientsOrTools<ROOM, RECIPE, POS> floor() {
        // If a single room needs supplies (for example) for BOTH states 0 and 1, it should only
        // show up as "needing" 0.
        Map<Integer, Collection<IRoomRecipeMatch<ROOM, RECIPE, POS, ?>>> b = new HashMap<>();
        inner.forEach((k, rooms) -> {
            ImmutableSet.Builder<IRoomRecipeMatch<ROOM, RECIPE, POS, ?>> allPrevRooms = ImmutableSet.builder();
            for (int i = 0; i < k; i++) {
                Collection<IRoomRecipeMatch<ROOM, RECIPE, POS, ?>> elements = b.get(i);
                if (elements == null) {
                    elements = ImmutableList.of();
                }
                allPrevRooms.addAll(elements);
            }
            ImmutableSet<IRoomRecipeMatch<ROOM, RECIPE, POS, ?>> prevRooms = allPrevRooms.build();
            ImmutableList.Builder<IRoomRecipeMatch<ROOM, RECIPE, POS, ?>> bld = ImmutableList.builder();
            rooms.forEach(room -> {
                if (prevRooms.contains(room)) {
                    return;
                }
                bld.add(room);
            });
            ImmutableList<IRoomRecipeMatch<ROOM, RECIPE, POS, ?>> build = bld.build();
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

    public Collection<IRoomRecipeMatch<ROOM, RECIPE, POS, ?>> get(Integer s) {
        return null;
    }

    public ImmutableList<IRoomRecipeMatch<ROOM, RECIPE, POS, ?>> getMatches() {
        return ImmutableList.copyOf(inner.values().stream().flatMap(Collection::stream).iterator());
    }
}
