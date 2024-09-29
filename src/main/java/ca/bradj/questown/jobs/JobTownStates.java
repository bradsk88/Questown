package ca.bradj.questown.jobs;

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
                // FIXME: Not working for fetcher
                return new JobStatusesHelpers.PrePopDep<>("town has supplies", town::hasSupplies);
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
            public LZCD.Dependency<Void> canUseMoreSupplies() {
                return new SupplyNeed(town.roomsNeedingIngredientsByStateV2());
            }
        };
    }

    private static class SupplyNeed implements LZCD.Dependency<Void> {
        private static final String NAME = "town has rooms needing supplies";

        private final Map<Integer, LZCD.Dependency<Void>> rooms;
        private final Map<Integer, LZCD.Populated<WithReason<@Nullable Boolean>>> roomCache = new HashMap<>();

        public <ROOM extends Room> SupplyNeed(Map<Integer, LZCD.Dependency<Void>> integerCollectionMap) {
            this.rooms = integerCollectionMap;
        }

        @Override
        public LZCD.Populated<WithReason<@Nullable Boolean>> populate() {
            for (Integer i : rooms.keySet()) {
                roomCache.put(i, rooms.get(i).populate());
            };
            ImmutableMap.Builder<String, Object> b = ImmutableMap.builder();
            roomCache.forEach((k, v) -> b.put(k.toString(), v));
            return new LZCD.Populated<>(
                    getName(),
                    apply(() -> null),
                    b.build(),
                    null
            );
        }

        @Override
        public String describe() {
            StringBuilder b = new StringBuilder(NAME).append("{");
            for (Integer k : rooms.keySet()) {
                b.append("\n\tStage: ").append(k).append(", RoomsNeedingIngredients=[");
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
                LZCD.Populated<WithReason<@Nullable Boolean>> cacheGet = roomCache.get(i);
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
            return WithReason.always(
                    Boolean.FALSE,
                    "No rooms found needing supplies for states [" +
                            Strings.join(rooms.keySet().stream().map(Object::toString).toList(), ",") +
                            "]"
            );
        }
    }
}
