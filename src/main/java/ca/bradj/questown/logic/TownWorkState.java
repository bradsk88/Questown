package ca.bradj.questown.logic;

import ca.bradj.questown.jobs.production.Valued;
import ca.bradj.questown.mc.Util;
import ca.bradj.questown.town.workstatus.State;
import ca.bradj.roomrecipes.core.Room;
import ca.bradj.roomrecipes.core.space.Position;
import ca.bradj.roomrecipes.logic.InclusiveSpaces;
import org.apache.logging.log4j.util.TriConsumer;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.BiPredicate;
import java.util.function.Function;
import java.util.function.Supplier;

public class TownWorkState {
    public static <STATUS extends Valued<STATUS>, ROOM extends Room> void updateForSpecialRooms(
            Supplier<? extends Map<STATUS, ? extends Collection<ROOM>>> roomsWhereSpecialRulesApply,
            BiPredicate<Position, ROOM> isJobBlock,
            Function<STATUS, @NotNull Integer> workRequiredAtStates,
            BiFunction<Position, ROOM, State> getState,
            TriConsumer<Position, ROOM, State> setState,
            Function<STATUS, Boolean> toolsRequired,
            Map<STATUS, Boolean> invItemStatus
    ) {
        roomsWhereSpecialRulesApply.get().forEach(
                (status, rooms) -> rooms.forEach(
                        room -> InclusiveSpaces.getAllEnclosedPositions(room.getSpace()).forEach(
                                block -> {
                                    if (isJobBlock.test(block, room)) {
                                        State jobBlockState = getState.apply(block, room);
                                        Boolean toolzRequired = toolsRequired.apply(status);
                                        toolzRequired = toolzRequired != null && toolzRequired;
                                        Boolean hasSupplies = Util.getOrDefault(invItemStatus, status, false);
                                        if (toolzRequired && !hasSupplies) {
                                            setState.accept(
                                                    block,
                                                    room,
                                                    findHighestSuppliedStatus(workRequiredAtStates, invItemStatus, status)
                                            );
                                            return;
                                        }
                                        if (jobBlockState == null) {
                                            int wl = workRequiredAtStates.apply(status.minusValue(status.value()));
                                            State fresh = State.fresh();
                                            setState.accept(block, room, fresh.setWorkLeft(wl));
                                        }
                                    }
                                }
                        )
                )
        );

    }

    private static <STATUS extends Valued<STATUS>> State findHighestSuppliedStatus(
            Function<STATUS, @NotNull Integer> workRequiredAtStates,
            Map<STATUS, Boolean> supplyItemStatus,
            STATUS status
    ) {
        for (int i = 0; i < status.value(); i++) {
            STATUS prev = status.minusValue(i);
            Boolean b = supplyItemStatus.get(prev);
            if (b != null && b) {
                Integer wl = workRequiredAtStates.apply(prev);
                return State.freshAtState(prev.value()).setWorkLeft(wl);
            }
        }
        return State.fresh().setWorkLeft(workRequiredAtStates.apply(status.minusValue(status.value())));
    }
}
