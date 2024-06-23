package ca.bradj.questown.logic;

import ca.bradj.questown.jobs.production.ProductionStatus;
import ca.bradj.questown.jobs.production.TownNeedsMap;
import ca.bradj.questown.town.workstatus.State;
import ca.bradj.roomrecipes.adapter.RoomWithBlocks;
import ca.bradj.roomrecipes.core.Room;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

public class TownNeeds {
    public static <POS, BLOCK> TownNeedsMap<ProductionStatus> getRoomsNeedingIngredientsOrTools(
            Map<ProductionStatus, ? extends Emptyable> ingredientsRequiredAtStates,
            Map<ProductionStatus, Integer> ingredientQtyRequiredAtStates,
            Map<ProductionStatus, ? extends Emptyable> toolsRequiredAtStates,
            Function<ProductionStatus, @NotNull Collection<? extends RoomWithBlocks<? extends Room, POS, BLOCK>>> roomsWithState,
            Map<ProductionStatus, ? extends Collection<? extends Room>> roomsWhereSpecialRulesApply,
            Function<POS, @Nullable State> states,
            Predicate<POS> canClaim,
            Function<ProductionStatus, @Nullable Boolean> hasRequiredTool,
            int maxState
    ) {
        // TODO: Try delegating to MCTownStateWorldInteraction.hasSupplies
        ImmutableMap.Builder<ProductionStatus, ImmutableList<Room>> workable = ImmutableMap.builder();
        ImmutableMap.Builder<ProductionStatus, ImmutableList<Room>> suppliable = ImmutableMap.builder();
        boolean prevToolStatus = true;
        for (int i = 0; i <= maxState; i++) {
            ArrayList<Room> forSuppliable = new ArrayList<>();
            ArrayList<Room> forWorkable = new ArrayList<>();
            Consumer<Collection<? extends Room>> addToSupply = forSuppliable::addAll;
            Consumer<Collection<? extends Room>> addToWorks = forWorkable::addAll;
            ProductionStatus s = ProductionStatus.fromJobBlockStatus(i);
            Emptyable ingr = ingredientsRequiredAtStates.get(s);
            Emptyable tool = toolsRequiredAtStates.get(s);
            Collection<Room> specialRooms = getRooms(roomsWhereSpecialRulesApply, s);
            if (ingr == null && tool == null) {
                workable.put(s, ImmutableList.copyOf(specialRooms));
                suppliable.put(s, ImmutableList.copyOf(specialRooms));
                continue;
            }
            boolean iPrev = prevToolStatus;
            Boolean iTool = hasRequiredTool.apply(s);
            boolean hasRequiredToolForStage = prevToolStatus;
            if (iTool != null) {
                prevToolStatus = iTool;
                hasRequiredToolForStage = iTool;
            }
            if (ingr == null) {
                ingr = () -> true;
            }
            if (!hasRequiredToolForStage) {
                addToWorks = (x) -> {
                };
            }
            Collection<? extends RoomWithBlocks<? extends Room, POS, BLOCK>> matches = roomsWithState.apply(s);
            final int ii = i;
            List<? extends Room> roomz = matches.stream().filter(room -> {
                for (Map.Entry<POS, BLOCK> e : room.containedBlocks.entrySet()) {
                    State jobBlockState = states.apply(e.getKey());
                    if (jobBlockState == null) {
                        continue;
                    }
                    if (jobBlockState.processingState() != ii) {
                        continue;
                    }
                    if (!canClaim.test(e.getKey())) {
                        continue;
                    }
                    Integer qtyReq = ingredientQtyRequiredAtStates.get(s);
                    if (qtyReq == null || qtyReq == 0 || jobBlockState.ingredientCount() < qtyReq) {
                        return true;
                    }
                }
                return false;
            }).map(v -> v.room).toList();
            addToWorks.accept(roomz);
            addToSupply.accept(roomz);
            Collection<? extends Room> sr = specialRooms;
            if (sr != null) {
                addToSupply.accept(sr);
                addToWorks.accept(sr);
            }
            workable.put(s, ImmutableList.copyOf(forWorkable));
            suppliable.put(s, ImmutableList.copyOf(forSuppliable));
        }
        return new TownNeedsMap<>(workable.build(), suppliable.build());
    }

    private static Collection<Room> getRooms(
            Map<ProductionStatus, ? extends Collection<? extends Room>> roomsWhereSpecialRulesApply,
            ProductionStatus s
    ) {
        Collection<? extends Room> x = roomsWhereSpecialRulesApply.get(s);
        if (x == null) {
            return ImmutableList.of();
        }
        ImmutableList.Builder<Room> b = ImmutableList.builder();
        x.forEach(b::add);
        return b.build();
    }
}
