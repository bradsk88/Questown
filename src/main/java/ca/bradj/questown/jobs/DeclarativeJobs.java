package ca.bradj.questown.jobs;

import ca.bradj.questown.core.Pair;
import ca.bradj.questown.core.UtilClean;
import ca.bradj.questown.integration.minecraft.MCContainer;
import ca.bradj.questown.integration.minecraft.MCHeldItem;
import ca.bradj.questown.integration.minecraft.MCTownItem;
import ca.bradj.questown.integration.minecraft.MCTownState;
import ca.bradj.questown.jobs.MCTownStateWorldInteraction.Inputs;
import ca.bradj.questown.jobs.declarative.ProductionJournal;
import ca.bradj.questown.jobs.declarative.WithReason;
import ca.bradj.questown.jobs.leaver.ContainerTarget;
import ca.bradj.questown.jobs.production.ProductionStatus;
import ca.bradj.questown.jobs.production.RoomsNeedingVillagerInput;
import ca.bradj.questown.jobs.production.RoomsNeedingVillagerInput.NVIRoom;
import ca.bradj.questown.logic.PredicateCollection;
import ca.bradj.questown.roomrecipes.Spaces;
import ca.bradj.questown.town.Warper;
import ca.bradj.questown.town.interfaces.TownInterface;
import ca.bradj.questown.town.interfaces.WorkStatusHandle;
import ca.bradj.questown.town.workstatus.State;
import ca.bradj.roomrecipes.adapter.Positions;
import ca.bradj.roomrecipes.adapter.RoomRecipeMatch;
import ca.bradj.roomrecipes.core.space.Position;
import ca.bradj.roomrecipes.serialization.MCRoom;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.function.*;
import java.util.stream.Stream;

public class DeclarativeJobs {

    public static final IProductionStatusFactory<ProductionStatus> STATUS_FACTORY = new IProductionStatusFactory<>() {
        @Override
        public ProductionStatus fromJobBlockState(int s) {
            return ProductionStatus.fromJobBlockStatus(s);
        }

        @Override
        public ProductionStatus waitingForTimedState() {
            return ProductionStatus.FACTORY.waitingForTimedState();
        }

        @Override
        public ProductionStatus noWorkPossible() {
            return ProductionStatus.FACTORY.noWorkPossible();
        }

        @Override
        public ProductionStatus droppingLoot() {
            return ProductionStatus.FACTORY.droppingLoot();
        }

        @Override
        public ProductionStatus noSpace() {
            return ProductionStatus.FACTORY.noSpace();
        }

        @Override
        public ProductionStatus goingToJobSite() {
            return ProductionStatus.FACTORY.goingToJobSite();
        }

        @Override
        public ProductionStatus noJobSite() {
            return ProductionStatus.FACTORY.noJobSite();
        }

        @Override
        public ProductionStatus noSupplies() {
            return ProductionStatus.FACTORY.noSupplies();
        }

        @Override
        public ProductionStatus collectingSupplies() {
            return ProductionStatus.FACTORY.collectingSupplies();
        }

        @Override
        public ProductionStatus idle() {
            return ProductionStatus.FACTORY.idle();
        }

        @Override
        public ProductionStatus extractingProduct() {
            return ProductionStatus.FACTORY.extractingProduct();
        }

        @Override
        public ProductionStatus relaxing() {
            return ProductionStatus.FACTORY.relaxing();
        }
    };

    private static final AbstractDeclarativeJobWarper<MCTownState, MCRoom, BlockPos, ServerLevel> WARPER = new AbstractDeclarativeJobWarper<>() {
    };

    public static <INGREDIENT, ITEM extends Item<ITEM>, HELD_ITEM extends HeldItem<HELD_ITEM, ITEM>> Map<Integer, Boolean> getSupplyItemStatus(
            Collection<HELD_ITEM> journalItems,
            ImmutableMap<Integer, INGREDIENT> ingredientsRequiredAtStates,
            ImmutableMap<Integer, INGREDIENT> toolsRequiredAtStates,
            BiPredicate<INGREDIENT, HELD_ITEM> matchFn
    ) {
        // TODO: Compare with JobsClean version and eliminate one
        HashMap<Integer, Boolean> b = new HashMap<>();
        BiConsumer<Integer, INGREDIENT> fn = (state, ingr) -> {
            if (ingr == null) {
                if (!b.containsKey(state)) {
                    b.put(state, false);
                }
                return;
            }

            // The check passes if the worker has ALL the ingredients needed for the state
            boolean has = journalItems.stream().anyMatch(v -> matchFn.test(ingr, v));
            if (!b.getOrDefault(state, false)) {
                b.put(state, has);
            }
        };
        ingredientsRequiredAtStates.forEach(fn);
        toolsRequiredAtStates.forEach(fn);
        return ImmutableMap.copyOf(b);
    }

    public static BiFunction<Integer, SignalSource, ProductionJournal<MCTownItem, MCHeldItem>> journalInitializer(JobID jobId) {
        return (capacity, signalSource) -> new ProductionJournal<>(
                jobId,
                signalSource,
                capacity,
                MCHeldItem::Air,
                STATUS_FACTORY
        );
    }

    public record Rooms(Map<BlockPos, Integer> spotStatuses, Map<MCRoom, ? extends Collection<Integer>> roomStatuses,
                        Map<BlockPos, Boolean> spotJobBlocks) {
    }

    // TODO: Unit test. This calculation is fairly sensitive and critical.
    public static ImmutableMap<Integer, RoomsWithWorkableStatefulBlocks> rooms(
            @NotNull Integer maxState,
            RoomsNeedingVillagerInput<MCRoom, ResourceLocation, BlockPos> roomHandle,
            WorkStatusHandle<BlockPos, MCHeldItem> work,
            Predicate<BlockPos> isJobBlock
    ) {
        ImmutableMap.Builder<Integer, RoomsWithWorkableStatefulBlocks> b = ImmutableMap.builder();
        Supplier<Rooms> e = () -> {
            ImmutableMap.Builder<BlockPos, Integer> spotStatuses = ImmutableMap.builder();
            ImmutableMap.Builder<BlockPos, Boolean> spotJBs = ImmutableMap.builder();
            Map<MCRoom, List<Integer>> roomStatuses = new HashMap<>();
            Stream<NVIRoom<MCRoom, ResourceLocation, BlockPos>> rooms = roomHandle.getMatches().stream();

            //TODO: Validate that this is actually needed
            rooms = rooms.filter(v -> !v.dueToWorkOnly());

            rooms.forEach(match -> {
                for (Map.Entry<BlockPos, ?> entry : match.room().getContainedBlocks().entrySet()) {
                    BlockPos bp = entry.getKey();
                    State jobBlockState = work.getJobBlockState(bp);
                    if (jobBlockState == null) {
                        continue;
                    }
                    int v = jobBlockState.processingState();
                    spotStatuses.put(bp, v);
                    UtilClean.addOrInitializeList(roomStatuses, match.room().getRoom(), v);
                    spotJBs.put(bp, isJobBlock.test(bp));
                }
            });
            return new Rooms(spotStatuses.build(), roomStatuses, spotJBs.build());
        };

        for (int i = 0; i < maxState; i++) {
            b.put(i, new RoomsWithWorkableStatefulBlocks(i, e));
        }
        return b.build();
    }

    public static LZCD.Dependency<Void> supplies(
            ServerLevel level,
            Supplier<? extends Map<Integer, ? extends LZCD.Dependency<Void>>> roomsHaveWorkableBlocks,
            TownInterface rooms,
            Map<Integer, PredicateCollection<MCHeldItem, MCHeldItem>> ingredients,
            Map<Integer, PredicateCollection<MCTownItem, MCTownItem>> tools,
            Predicate<RoomRecipeMatch<MCRoom>> shouldGetSuppliesFromRoom,
            Predicate<BlockPos> isJobBlock,
            Predicate<ResourceLocation> isJobSite
    ) {
        return new SimpleDependency("town has supplies") {

            @Override
            public String describe() {
                return "TODO"; // TODO?
            }

            @Override
            protected Populated<WithReason<Boolean>> doPopulate(boolean stopOnTrue) {
                ImmutableMap.Builder<String, Object> b = ImmutableMap.builder();
                Map<Integer, ? extends LZCD.Dependency<Void>> needs = roomsHaveWorkableBlocks.get();
                b.put("room needs", needs);

                List<PredicateCollection<MCHeldItem, MCHeldItem>> neededIngredients = new ArrayList<>();
                List<PredicateCollection<MCTownItem, MCTownItem>> neededTools = new ArrayList<>();
                for (Map.Entry<Integer, ? extends LZCD.Dependency<Void>> v : needs.entrySet()) {
                    Integer state = v.getKey();
                    if (!v.getValue().apply(() -> null).value) {
                        continue;
                    }
                    PredicateCollection<MCHeldItem, MCHeldItem> ingt = ingredients.get(state);
                    if (ingt != null) {
                        neededIngredients.add(ingt);
                    }
                    PredicateCollection<MCTownItem, MCTownItem> tool = tools.get(state);
                    if (tool != null) {
                        neededTools.add(tool);
                    }

                }

                b.put("relevant ingredients", neededIngredients);
                b.put("relevant tools", neededTools);

                List<ContainerTarget<MCContainer, MCTownItem>> containers = Containers.get(
                        rooms,
                        shouldGetSuppliesFromRoom,
                        isJobBlock,
                        isJobSite,
                        false
                );

                b.put("containers", containers);

                @Nullable WithReason<Boolean> found = null;
                Map<String, Object> b2 = new HashMap<>();

                for (ContainerTarget<MCContainer, MCTownItem> c : containers) {

                    Position position = Positions.FromBlockPos(c.getBlockPos());
                    String dPos = position.getUIString();
                    for (MCTownItem i : c.getItems()) {
                        if (i.isEmpty()) {
                            continue;
                        }
                        if (b2.get(dPos) != null && Boolean.TRUE.equals(b2.get(dPos))) {
                            continue;
                        }
                        MCHeldItem iHeld = MCHeldItem.fromTown(i);
                        Optional<?> matchedIngredient = neededIngredients.stream().filter(ing -> ing.test(iHeld))
                                                                         .findFirst();
                        String result = matchedIngredient.map(Object::toString).orElse("No match");
                        b2.put(dPos, new Pair<>(result, c.toShortString(false)));
                        if (matchedIngredient.isPresent()) {
                            found = WithReason.always(true, i.getShortName() + " matches " + matchedIngredient.get());
                            if (stopOnTrue) {
                                break;
                            }
                        }
                        Optional<?> matchedTool = neededTools.stream().filter(ing -> ing.test(i)).findFirst();
                        result = matchedTool.map(Object::toString).orElse("No match");
                        b2.put(dPos, new Pair<>(result, c.toShortString(false)));
                        if (matchedTool.isPresent()) {
                            found = WithReason.always(true, i.getShortName() + " matches " + matchedTool.get());
                            if (stopOnTrue) {
                                break;
                            }
                        }
                    }
                    if (found != null && stopOnTrue) {
                        break;
                    }
                }

                if (found == null) {
                    found = WithReason.always(false, "No matches found for " + ingredients + " in any containers");
                }

                b.put("supply checks", ImmutableMap.copyOf(b2));
                b.put("predicate", ingredients);
                ImmutableMap<String, Object> build = b.build();
                return new Populated<>("town has supplies", found, build, null) {
                    @Override
                    protected String stringRep() {
                        return "town has supplies [" + build + "]";
                    }
                };
            }
        };
    }


    private record HandlerInputs(MCTownStateWorldInteraction wi, Inputs inState, ProductionStatus status,
                                 State workBlockState, Integer maxState, BlockPos fakePos) {
    }

    public static void staticInitialize() {
        AbstractDeclarativeJobWarper.staticInitialize();
    }

    public static Warper<ServerLevel, MCTownState> warper(
            MCTownStateWorldInteraction wi,
            int maxState,
            boolean prioritizeExtraction
    ) {
        AbstractDeclarativeJobWarper.sanityCheck();

        return new Warper<>() {
            @Override
            public MCTownState warp(
                    ServerLevel level,
                    MCTownState inState,
                    long currentTick,
                    long ticksPassed,
                    int villagerNum
            ) {
                BlockPos fakePos = new BlockPos(villagerNum, villagerNum, villagerNum);
                MCRoom fakeRoom = Spaces.metaRoomAround(fakePos, 1);
                RoomRecipeMatch<MCRoom> fakeMatch = new RoomRecipeMatch<>(
                        fakeRoom,
                        ImmutableList.of(new ResourceLocation("fake")),
                        ImmutableList.of()
                );
                AbstractDeclarativeJobWarper.WorkSpotStandIn<MCTownState, BlockPos> ws = new AbstractDeclarativeJobWarper.WorkSpotStandIn<>() {
                    @Override
                    public BlockPos get() {
                        return new BlockPos(villagerNum, villagerNum, villagerNum);
                    }

                    @Override
                    public State getState(MCTownState mcTownState) {
                        return mcTownState.getJobBlockState(fakePos);
                    }

                    @Override
                    public MCTownState setState(
                            MCTownState outState,
                            State newValue
                    ) {
                        return outState.setJobBlockState(fakePos, newValue);
                    }

                    @Override
                    public MCTownState withTimerReducedBy(
                            MCTownState outState,
                            int ticksPassed
                    ) {
                        return outState.withTimerReducedBy(fakePos, ticksPassed);
                    }
                };

                Function<MCTownState, Inputs> inputs = zstate -> new Inputs(
                        zstate,
                        level,
                        zstate.getVillager(villagerNum).uuid
                );

                AbstractStateInteraction<Inpoots<MCTownState, ServerLevel>, BlockPos, ?, ?, MCTownState> wii = wi;
                // Inject ticks to bypass the rate limiter during warp
                // Without this, tryWorking returns null on most ticks due to interval check
                wi.injectTicks(wi.interval);
                return DeclarativeJobs.WARPER.warp(
                        ws,
                        inState,
                        Tick.at(currentTick).after(ticksPassed),
                        zstate -> wi.asInventory(
                                () -> wi.getHeldItems(moreSilly(inputs.apply(zstate)), villagerNum),
                                () -> ws.getState(zstate).processingState()
                        ),
                        ztate -> wi.asTownJobs(ws.getState(ztate), fakeMatch, fakePos, ztate.containers),
                        DeclarativeJobs.alwaysInRoom(fakeRoom),
                        prioritizeExtraction,
                        wii,
                        t -> new Inpoots<>(t, level, t.getVillager(villagerNum).uuid),
                        maxState
                );
            }

            @Override
            public Collection<Tick> getTicks(
                    long referenceTick,
                    long ticksPassed
            ) {
                return computeWarpTicks(referenceTick, ticksPassed, wi.interval, wi.getMinTimerValue());
            }
        };
    }

    /**
     * Computes the tick milestones for a time warp operation.
     * This is a pure function extracted for testability.
     *
     * @param referenceTick The starting game tick
     * @param ticksPassed   Total ticks to warp forward
     * @param workInterval  The work interval from the world interaction
     * @return Collection of tick milestones to process during warp
     */
    public static Collection<Warper.Tick> computeWarpTicks(
            long referenceTick,
            long ticksPassed,
            int workInterval
    ) {
        return computeWarpTicks(referenceTick, ticksPassed, workInterval, null);
    }

    /**
     * Computes the tick milestones for a time warp operation.
     * This is a pure function extracted for testability.
     *
     * @param referenceTick  The starting game tick
     * @param ticksPassed    Total ticks to warp forward
     * @param workInterval   The work interval from the world interaction
     * @param minTimerValue  The minimum timer value from job definition, or null if no timers
     * @return Collection of tick milestones to process during warp
     */
    public static Collection<Warper.Tick> computeWarpTicks(
            long referenceTick,
            long ticksPassed,
            int workInterval,
            @Nullable Integer minTimerValue
    ) {
        ImmutableList.Builder<Warper.Tick> b = ImmutableList.builder();

        long start = referenceTick;
        long max = referenceTick + ticksPassed;

        // Walk heuristic: double the work interval to account for travel time
        int walkHeuristic = workInterval * 2;

        // If timers exist, constrain step interval to catch timer completions
        int timerConstraint = (minTimerValue != null && minTimerValue > 0)
                ? minTimerValue
                : Integer.MAX_VALUE;

        // Use the smaller of walk heuristic or timer constraint, with 100 tick minimum
        int stepInterval = Math.max(Math.min(walkHeuristic, timerConstraint), 100);

        for (long i = start; i <= max; i += stepInterval) {
            b.add(new Warper.Tick(i, stepInterval));
        }
        return b.build();
    }

    private static Inpoots<MCTownState, ServerLevel> moreSilly(Inputs apply) {
        return new Inpoots<>(apply.town(), apply.level(), apply.vUUID());
    }

    private static EntityLocStateProvider<MCRoom> alwaysInRoom(
            MCRoom fakeRoom
    ) {
        return new EntityLocStateProvider<MCRoom>() {
            @Override
            public @Nullable MCRoom getEntityCurrentJobSite() {
                return fakeRoom;
            }
        };
    }

    public static final class RoomsWithWorkableStatefulBlocks implements LZCD.Dependency<Void> {

        private static final String NAME = "rooms contain workable blocks with state";

        private final Supplier<Rooms> inputs;
        private final String name;
        private final int state;
        private Populated<WithReason<Boolean>> value;

        public RoomsWithWorkableStatefulBlocks(
                int state,
                Supplier<Rooms> inputs
        ) {
            this.inputs = inputs;
            this.name = NAME + " " + state;
            this.state = state;
        }

        @Override
        public Populated<WithReason<@Nullable Boolean>> populate() {
            // TODO[Performance: Cache?
//            if (value != null) {
//                return value;
//            }
            Rooms v = this.inputs.get();
            Map<BlockPos, Integer> spotStates = v.spotStatuses();

            List<Map.Entry<BlockPos, Integer>> spotsWithMatchingState = spotStates.entrySet().stream()
                                                                                  .filter(z -> state == z.getValue())
                                                                                  .toList();

            List<Map.Entry<BlockPos, Boolean>> spotsThatAreJobBlocks = v.spotJobBlocks().entrySet().stream()
                                                                        .filter(Map.Entry::getValue).toList();

            // Find the first spot that is in both lists
            Optional<Map.Entry<BlockPos, Integer>> foundSpot = spotsWithMatchingState.stream()
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
            spotStates.forEach((k, vv) -> css.put(k.toShortString(), vv));
            ImmutableMap.Builder<String, Object> cjs = ImmutableMap.builder();
            v.spotJobBlocks().forEach((k, vv) -> cjs.put(k.toShortString(), vv));
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
}
