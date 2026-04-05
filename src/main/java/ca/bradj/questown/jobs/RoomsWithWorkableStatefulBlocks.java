package ca.bradj.questown.jobs;

import ca.bradj.questown.jobs.declarative.WithReason;
import com.google.common.collect.ImmutableMap;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;

public final class RoomsWithWorkableStatefulBlocks<POS> implements LZCD.Dependency<Void> {

    private static final String NAME = "rooms contain workable blocks with state";

    private final Supplier<Rooms<POS, ?>> inputs;
    private final String name;
    private final int state;
    private final Function<POS, String> stringify;
    private Populated<WithReason<Boolean>> value;

    public RoomsWithWorkableStatefulBlocks(
            int state,
            Supplier<Rooms<POS, ?>> inputs,
            Function<POS, String> stringify
    ) {
        this.inputs = inputs;
        this.name = NAME + " " + state;
        this.state = state;
        this.stringify = stringify;
    }

    @Override
    public Populated<WithReason<@Nullable Boolean>> populate() {
        // TODO[Performance]: Cache?
//            if (value != null) {
//                return value;
//            }
        Rooms<POS, ?> v = this.inputs.get();
        Map<POS, Integer> spotStates = v.spotStatuses();

        List<Map.Entry<POS, Integer>> spotsWithMatchingState = spotStates.entrySet().stream()
                                                                              .filter(z -> state == z.getValue())
                                                                              .toList();

        List<Map.Entry<POS, Boolean>> spotsThatAreJobBlocks = v.spotJobBlocks().entrySet().stream()
                                                                    .filter(Map.Entry::getValue).toList();

        // Find the first spot that is in both lists
        Optional<Map.Entry<POS, Integer>> foundSpot = spotsWithMatchingState.stream()
                                                                                 .filter(z -> spotsThatAreJobBlocks.stream()
                                                                                                                   .anyMatch(
                                                                                                                           vv -> vv.getKey()
                                                                                                                                   .equals(z.getKey())))
                                                                                 .findFirst();

        WithReason<Boolean> hasSpot = foundSpot.map(zz -> WithReason.always(
                true,
                "town has workable spot with state at " + foundSpot.get().getKey()
        )).orElse(WithReason.always(false, "no spots found"));

        ImmutableMap.Builder<String, Object> css = ImmutableMap.builder();
        spotStates.forEach((k, vv) -> css.put(stringify.apply(k), vv));
        ImmutableMap.Builder<String, Object> cjs = ImmutableMap.builder();
        v.spotJobBlocks().forEach((k, vv) -> cjs.put(stringify.apply(k), vv));
        ImmutableMap.Builder<String, Object> crs = ImmutableMap.builder();
        v.roomStatuses().forEach((k, vv) -> crs.put(k.doorPos.getUIString(), vv));

        ImmutableMap<String, Object> bSpots = css.build();
        ImmutableMap<String, Object> jBlocks = cjs.build();
        ImmutableMap<String, Object> bRooms = crs.build();

        // Capturing this data makes it easier to debug
        this.value = new Populated<>(
                name,
                hasSpot,
                ImmutableMap.of("spots", bSpots, "rooms", bRooms, "job_blocks", jBlocks),
                null
        ) {
            @Override
            protected String stringRep() {
                return "RoomsWithState=[" + bRooms + "]";
            }
        };
        return value;
    }

    @Override
    public String describe() {
        return "RoomsContainWorkState=" + value.value();
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public WithReason<Boolean> apply(Supplier<Void> voidSupplier) {
        return this.populate().value();
    }

    @Override
    public String toString() {
        return describe();
    }
}
