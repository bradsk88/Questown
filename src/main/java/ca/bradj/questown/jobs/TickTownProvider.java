package ca.bradj.questown.jobs;

import ca.bradj.questown.jobs.production.RoomsNeedingVillagerInput;
import ca.bradj.roomrecipes.adapter.IRoomRecipeMatch;
import ca.bradj.roomrecipes.core.Room;
import com.google.common.collect.ImmutableList;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

public class TickTownProvider<ROOM extends Room, POS, MATCH extends IRoomRecipeMatch<ROOM, ?, POS, ?>> implements JobTownProvider<ROOM> {

    private final Supplier<ImmutableList<MATCH>> roomFinder;


    public TickTownProvider(
            Supplier<ImmutableList<MATCH>> roomFinder
    ) {
        this.roomFinder = roomFinder;
    }

    @Override
    public Collection<ROOM> roomsWithCompletedProduct() {
        return roomFinder.get().stream().map(v -> v.getRoom()).toList();
    }
    private final Function<BlockPos, State> getJobBlockState = work::getJobBlockState;

    @Override
    public RoomsNeedingVillagerInput<MCRoom, ResourceLocation, BlockPos> roomsNeedingIngredientsByState() {
        return roomsNeedingIngredientsOrTools;
    }

    @Override
    public Map<Integer, ? extends LZCD.Dependency<Void>> roomsWithWorkableStatefulBlocks() {
        return roomsV2.get();
    }

    @Override
    public boolean isUnfinishedTimeWorkPresent() {
        return Jobs.isUnfinishedTimeWorkPresent(
                extra.town().getRoomHandle(),
                town.getRoomHandle(),
                location.baseRoom(),
                work::getTimeToNextState
        );
    }

    @Override
    public Collection<Integer> getStatesWithUnfinishedItemlessWork() {
        Collection<Integer> statesWithUnfinishedWork = Jobs.getStatesWithUnfinishedWork(
                () -> extra.town().getRoomHandle().getRoomsMatching(location.baseRoom()).stream()
        () -> town.getRoomHandle().getRoomsMatching(location.baseRoom()).stream()
                  .map(v -> (Supplier<Collection<BlockPos>>) () -> v.getContainedBlocks().keySet()
                                                                    .stream()
                                                                    .filter(z -> shouldInit(z, extra))
                                                                    .toList())
                  .toList(), getJobBlockState, (bp) -> work.canClaim(bp, () -> makeClaim(ownerUUID))
                );
        ImmutableList.Builder<Integer> b = ImmutableList.builder();
        statesWithUnfinishedWork.forEach(s -> {
            IPredicateCollection<MCTownItem> toolsReq = checks.getToolsForStep(s);
            if (toolsReq != null && !toolsReq.isEmpty()) {
                return;
            }
            b.add(s);
        });
        return b.build();
    }

    @Override
    public Collection<MCRoom> roomsAtState(Integer state) {
        return roomsNeedingIngredientsOrTools.get().get(state).stream()
                                             .map(RoomsNeedingVillagerInput.NVIRoom::room)
                                             .map(IRoomRecipeMatch::getRoom).toList();
    }

    @Override
    public boolean hasSupplies() {
        RoomsNeedingVillagerInput<MCRoom, ResourceLocation, BlockPos> needs = roomsNeedingIngredientsByState();
        ImmutableList<PredicateCollection<MCTownItem, ?>> neededItems = needs.cleanFns(
                checks::getIngredientsForStep,
                checks::getToolsForStep
        );
        return Jobs.townHasSupplies(extra.town(), journal, neededItems);
        return Jobs.townHasSupplies(town, journal, neededItems);
    }

    @Override
    public LZCD.Dependency<Void> hasSuppliesV2() {
        return DeclarativeJobs.supplies(
                extra.town().getServerLevel(),
                town.getServerLevel(),
                roomsV2,
                extra.town(),
                town,
                checks.getAllRequiredIngredients(),
                checks.getAllRequiredTools(),
                checks::shouldCheckContainerForSupplies,
                bp -> isJobBlock(bp),
                js -> location.baseRoom().equals(js)
        );
    }

    @Override
    public boolean hasSpace() {
        return Jobs.townHasSpace(town);
    }
}
