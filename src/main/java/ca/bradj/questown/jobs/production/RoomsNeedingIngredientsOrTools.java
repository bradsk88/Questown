package ca.bradj.questown.jobs.production;

import ca.bradj.questown.integration.minecraft.MCHeldItem;
import ca.bradj.questown.integration.minecraft.MCTownItem;
import ca.bradj.questown.logic.PredicateCollection;
import ca.bradj.questown.mc.PredicateCollections;
import ca.bradj.roomrecipes.adapter.IRoomRecipeMatch;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;

import java.util.*;
import java.util.function.Function;
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
        return inner;
    }

    public ImmutableList<PredicateCollection<MCTownItem, ?>> cleanFns(
            Function<Integer, PredicateCollection<MCHeldItem, ?>> items,
            Function<Integer, PredicateCollection<MCTownItem, ?>> tools
    ) {
        // TODO: Be smarter? We're just finding the first room that needs stuff.
        Optional<Integer> first = inner.entrySet()
                                           .stream()
                                           .filter(v -> !v.getValue()
                                                          .isEmpty())
                                           .map(Map.Entry::getKey)
                                           .findFirst();

        if (first.isEmpty()) {
            return ImmutableList.of();
        }
        int s = first.get();

        ImmutableList.Builder<PredicateCollection<MCTownItem, ?>> bb = ImmutableList.builder();
        PredicateCollection<MCHeldItem, ?> ingr = items.apply(s);
        if (ingr != null) {
            bb.add(PredicateCollections.townify(ingr));
        }
        // Hold on to tools required for this state and all previous states
        for (int i = 0; i <= s; i++) {
            PredicateCollection<MCTownItem, ?> tool = tools.apply(i);
            if (tool != null) {
                bb.add(tool);
            }
        }
        return bb.build();
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
        return inner.containsKey(s);
    }

    public Collection<IRoomRecipeMatch<ROOM, RECIPE, POS, ?>> get(Integer s) {
        return inner.get(s);
    }

    public ImmutableList<IRoomRecipeMatch<ROOM, RECIPE, POS, ?>> getMatches() {
        return ImmutableList.copyOf(inner.values().stream().flatMap(Collection::stream).iterator());
    }
}
