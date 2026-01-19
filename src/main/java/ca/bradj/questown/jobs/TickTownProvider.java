package ca.bradj.questown.jobs;

import ca.bradj.questown.jobs.leaver.ContainerTarget;
import ca.bradj.questown.jobs.production.RoomsNeedingVillagerInput;
import ca.bradj.questown.logic.PredicateCollection;
import ca.bradj.questown.town.workstatus.State;
import ca.bradj.roomrecipes.adapter.IRoomRecipeMatch;
import ca.bradj.roomrecipes.core.Room;
import com.google.common.collect.ImmutableList;

import java.util.Collection;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

public class TickTownProvider<ROOM extends Room, POS, MATCH extends IRoomRecipeMatch<ROOM, ?, POS, ?>, HELD_ITEM, TOWN_ITEM extends Item<TOWN_ITEM>, CONTAINER extends ContainerTarget<?, TOWN_ITEM>> implements
        JobTownProvider<ROOM> {

    private final Supplier<ImmutableList<MATCH>> resultsFinder;
    private final Supplier<ImmutableList<MATCH>> roomsFinder;
    private final Function<POS, State> getJobBlockState;
    private final RoomsNeedingVillagerInput<ROOM, ?, POS> roomsNeedingVillagerInput;

    private final Supplier<Map<Integer, RoomsWithWorkableStatefulBlocks<POS>>> roomsV2;
    private final Function<POS, Integer> getTicksLeft;
    private final Predicate<POS> canClaim;
    private final Predicate<POS> isJobBlock;
    private final Function<Integer, PredicateCollection<HELD_ITEM, HELD_ITEM>> items;
    private final Function<Integer, PredicateCollection<TOWN_ITEM, TOWN_ITEM>> tools;
    private final BiFunction<ROOM, POS, ContainersClean.Block<CONTAINER>> toBlock;

    public <RECIPE> TickTownProvider(
            Supplier<ImmutableList<MATCH>> resultsFinder,
            Supplier<ImmutableList<MATCH>> roomsFinder,
            BiFunction<ROOM, POS, ContainersClean.Block<CONTAINER>> toBlock,
            Function<POS, State> getJobBlockState,
            Function<POS, Integer> getTicksLeft,
            RoomsNeedingVillagerInput<ROOM, RECIPE, POS> roomsNeedingIngredientsOrTools,
            Predicate<POS> isJobBlock,
            Predicate<POS> canClaim,
            Function<Integer, PredicateCollection<HELD_ITEM, HELD_ITEM>> items,
            Function<Integer, PredicateCollection<TOWN_ITEM, TOWN_ITEM>> tools,
            int maxState
    ) {
        this.resultsFinder = resultsFinder;
        this.roomsFinder = roomsFinder;
        this.getJobBlockState = getJobBlockState;
        this.getTicksLeft = getTicksLeft;
        this.roomsNeedingVillagerInput = roomsNeedingIngredientsOrTools;
        this.roomsV2 = () -> JobsClean.rooms(
                roomsNeedingIngredientsOrTools::getMatches,
                getJobBlockState,
                isJobBlock,
                maxState
        );
        this.canClaim = canClaim;
        this.isJobBlock = isJobBlock;
        this.items = items;
        this.tools = tools;
        this.toBlock = toBlock;
    }

    @Override
    public Collection<ROOM> roomsWithCompletedProduct() {
        return resultsFinder.get().stream().map(v -> v.getRoom()).toList();
    }

    @Override
    public RoomsNeedingVillagerInput<ROOM, ?, POS> roomsNeedingIngredientsByState() {
        return roomsNeedingVillagerInput;
    }

    @Override
    public Map<Integer, ? extends LZCD.Dependency<Void>> roomsWithWorkableStatefulBlocks() {
        return roomsV2.get();
    }

    @Override
    public boolean isUnfinishedTimeWorkPresent() {
        return JobsClean.isUnfinishedTimeWorkPresent(resultsFinder, getTicksLeft);
    }

    @Override
    public Collection<Integer> getStatesWithUnfinishedItemlessWork() {
        Collection<Integer> statesWithUnfinishedWork = JobsClean.getStatesWithUnfinishedWork(
                roomsFinder.get().stream()
                           .map(v -> (Supplier<Collection<POS>>) () -> v.getContainedBlocks().keySet().stream()
                                                                        .filter(isJobBlock).toList()).toList(),
                getJobBlockState,
                canClaim
        );
        ImmutableList.Builder<Integer> b = ImmutableList.builder();
        statesWithUnfinishedWork.forEach(s -> {
            PredicateCollection<?, ?> toolsReq = tools.apply(s);
            if (toolsReq != null && !toolsReq.isEmpty()) {
                return;
            }
            b.add(s);
        });
        return b.build();
    }

    @Override
    public Collection<ROOM> roomsAtState(Integer state) {
        return roomsNeedingVillagerInput.get().get(state).stream()
                                        .map(RoomsNeedingVillagerInput.NVIRoom::room)
                                        .map(IRoomRecipeMatch::getRoom).toList();
    }

    @Override
    public LZCD.Dependency<Void> hasSuppliesV2() {
//        return DeclarativeJobs.supplies(
//                extra.town().getServerLevel(),
//                town.getServerLevel(),
//                roomsV2,
//                extra.town(),
//                town,
//                checks.getAllRequiredIngredients(),
//                checks.getAllRequiredTools(),
//                checks::shouldCheckContainerForSupplies,
//                bp -> isJobBlock(bp),
//                js -> location.baseRoom().equals(js)
//        );
        return new TownHasSupplies<>(items, tools, this::getJobSites);
    }

    private ImmutableList<ContainersClean.JobSite<CONTAINER>> getJobSites() {
        return roomsFinder.get().stream().map(v -> new ContainersClean.JobSite<CONTAINER>() {
            @Override
            public ImmutableList<ContainersClean.Block<CONTAINER>> getBlocks() {
                return v.getContainedBlocks().keySet().stream().map(z -> toBlock.apply(v.getRoom(), z))
                        .collect(ImmutableList.toImmutableList());
            }

            @Override
            public boolean isJobSite() {
                return false;
            }
        }).collect(ImmutableList.toImmutableList());
    }

    @Override
    public boolean hasSpace() {
        return Jobs.townHasSpace(town);
    }
}
