package ca.bradj.questown.jobs;

import ca.bradj.questown.QT;
import ca.bradj.questown.jobs.declarative.WithReason;
import ca.bradj.roomrecipes.core.Room;
import com.google.common.collect.ImmutableMap;
import joptsimple.internal.Strings;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

public class JobTownStates {
    public static <ROOM extends Room> TownStateProvider forTown(JobTownProvider<ROOM> town) {
        return new TownStateProvider() {
            @Override
            public LZCD.Dependency<Void> hasSupplies() {
                return town.hasSuppliesV2();
            }

            @Override
            public LZCD.Dependency<Void> hasSpace() {
                return new JobStatusesHelpers.PrePopDep<>("town has storage space", town::hasSpace);
            }


            @Override
            public LZCD.Dependency<Void> isTimerActive() {
                return new JobStatusesHelpers.PrePopDep<>("town has active timer", town::isUnfinishedTimeWorkPresent);
            }

            @Override
            public LZCD.Dependency<Void> containsWorkableBlocksAtAnyState() {
                return new RoomsWithWorkableBlocksAtAnyState(town.roomsWithWorkableStatefulBlocks());
            }
        };
    }

    private static class RoomsWithWorkableBlocksAtAnyState implements LZCD.Dependency<Void> {
        private static final String NAME = "town has workable blocks for ANY WorkState";

        private final Map<Integer, ? extends LZCD.Dependency<Void>> rooms;
        private final Map<Integer, Populated<WithReason<@Nullable Boolean>>> roomCache = new HashMap<>();

        public RoomsWithWorkableBlocksAtAnyState(Map<Integer, ? extends LZCD.Dependency<Void>> rooms) {
            this.rooms = rooms;
        }

        @Override
        public Populated<WithReason<@Nullable Boolean>> populate() {
            for (Integer i : rooms.keySet()) {
                roomCache.put(i, rooms.get(i).populate());
            }
            ImmutableMap.Builder<String, Object> b = ImmutableMap.builder();
            roomCache.forEach((k, v) -> b.put(k.toString(), v));
            ImmutableMap<String, Object> build = b.build();
            return new Populated<>(
                    getName(),
                    apply(() -> null),
                    build,
                    null
            ) {
                @Override
                protected String stringRep() {
                    return "SupplyNeeds[" + build + "]";
                }
            };
        }

        @Override
        public String describe() {
            StringBuilder b = new StringBuilder(NAME).append("{");
            for (Integer k : rooms.keySet()) {
                b.append("\n\tStage: ").append(k).append(", RoomsWithWorkableBlocks=[");
                b.append(rooms.get(k).describe());
                b.append("]");
            }
            b.append("}");
            return b.toString();
        }

        @Override
        public String getName() {
            return NAME;
        }

        @Override
        public WithReason<Boolean> apply(Supplier<Void> voidSupplier) {
            for (Integer i : rooms.keySet()) {
                Populated<WithReason<@Nullable Boolean>> cacheGet = roomCache.get(i);
                if (cacheGet == null) {
                    LZCD.Dependency<Void> voidDependency = rooms.get(i);
                    WithReason<Boolean> checkedTown = voidDependency.apply(voidSupplier);
                    roomCache.put(i, voidDependency.populate());
                    if (checkedTown.value) {
                        return checkedTown;
                    }
                    continue;
                }
                if (Boolean.TRUE.equals(cacheGet.value().value())) {
                    return cacheGet.value();
                }
            }
            String reason = "No rooms found with workable blocks at states [" +
                    Strings.join(rooms.keySet().stream().map(Object::toString).toList(), ",") +
                    "]";
            // TODO[Decup]: Remove
            QT.JOB_LOGGER.debug("containsWorkableBlocksAtAnyState returning FALSE: {}", reason);
            return WithReason.always(Boolean.FALSE, reason);
        }
    }
}
