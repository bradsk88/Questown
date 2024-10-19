package ca.bradj.questown.jobs;

import ca.bradj.questown.core.UtilClean;
import ca.bradj.questown.integration.minecraft.MCContainer;
import ca.bradj.questown.integration.minecraft.MCHeldItem;
import ca.bradj.questown.integration.minecraft.MCTownItem;
import ca.bradj.questown.integration.minecraft.MCTownState;
import ca.bradj.questown.jobs.declarative.ProductionJournal;
import ca.bradj.questown.jobs.declarative.WithReason;
import ca.bradj.questown.jobs.leaver.ContainerTarget;
import ca.bradj.questown.jobs.production.ProductionStatus;
import ca.bradj.questown.jobs.production.ProductionStatuses;
import ca.bradj.questown.jobs.production.RoomsNeedingIngredientsOrTools;
import ca.bradj.questown.logic.PredicateCollection;
import ca.bradj.questown.mc.Util;
import ca.bradj.questown.roomrecipes.Spaces;
import ca.bradj.questown.town.TownContainers;
import ca.bradj.questown.town.Warper;
import ca.bradj.questown.town.interfaces.TownInterface;
import ca.bradj.questown.town.interfaces.WorkStatusHandle;
import ca.bradj.questown.town.workstatus.State;
import ca.bradj.roomrecipes.adapter.IRoomRecipeMatch;
import ca.bradj.roomrecipes.adapter.Positions;
import ca.bradj.roomrecipes.adapter.RoomRecipeMatch;
import ca.bradj.roomrecipes.core.space.Position;
import ca.bradj.roomrecipes.serialization.MCRoom;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.common.util.Lazy;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.function.*;

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

    private static ImmutableMap<ProductionStatus, Function<HandlerInputs, MCTownState>> handler;

    public static BiFunction<Integer, SignalSource, ProductionJournal<MCTownItem, MCHeldItem>> journalInitializer(JobID jobId) {
        return (capacity, signalSource) -> new ProductionJournal<>(
                jobId,
                signalSource,
                capacity,
                MCHeldItem::Air,
                STATUS_FACTORY
        );
    }

    public static ImmutableMap<Integer, LZCD.Dependency<Void>> rooms(
            @NotNull Integer maxState,
            RoomsNeedingIngredientsOrTools<MCRoom, ResourceLocation, BlockPos> roomHandle,
            WorkStatusHandle<BlockPos, MCHeldItem> work
    ) {
        ImmutableMap.Builder<Integer, LZCD.Dependency<Void>> b = ImmutableMap.builder();
        Supplier<UtilClean.Pair<Map<BlockPos, Integer>, Map<MCRoom, Collection<Integer>>>> e = () -> {
            ImmutableMap.Builder<BlockPos, Integer> spotStatuses = ImmutableMap.builder();
            Map<MCRoom, Collection<Integer>> roomStatuses = new HashMap<>();
            List<IRoomRecipeMatch<MCRoom, ResourceLocation, BlockPos, ?>> rooms = roomHandle.getMatches();

            rooms.forEach(match -> match.getContainedBlocks().forEach((bp, bv) -> {
                State jobBlockState = work.getJobBlockState(bp);
                if (jobBlockState == null) {
                    return;
                }
                spotStatuses.put(bp, jobBlockState.processingState());
                Util.addOrInitialize(roomStatuses, match.getRoom(), jobBlockState.processingState());
            }));
            return new UtilClean.Pair<>(spotStatuses.build(), roomStatuses);
        };

        for (int i = 0; i < maxState; i++) {
            b.put(i, new RoomStates(i, e));
        }
        return b.build();
    }

    public static LZCD.Dependency<Void> supplies(
            ServerLevel level,
            Supplier<Map<Integer, LZCD.Dependency<Void>>> roomStatuses,
            TownInterface rooms,
            Map<Integer, PredicateCollection<MCHeldItem, MCHeldItem>> ingredients,
            Map<Integer, PredicateCollection<MCTownItem, MCTownItem>> tools
    ) {
        return new LZCD.SimpleDependency("town has supplies") {

            @Override
            public String describe() {
                return "TODO"; // TODO?
            }

            @Override
            protected LZCD.Populated<WithReason<Boolean>> doPopulate(boolean stopOnTrue) {
                ImmutableMap.Builder<String, Object> b = ImmutableMap.builder();
                Map<Integer, LZCD.Dependency<Void>> needs = roomStatuses.get();
                b.put("room needs", needs);

                List<PredicateCollection<MCHeldItem, MCHeldItem>> neededIngredients = needs.
                        entrySet().
                        stream().
                        filter(v -> v.getValue().apply(() -> null).value()).
                        map(v -> ingredients.get(v.getKey())).
                        filter(Objects::nonNull).
                        toList();
                b.put("relevant ingredients", neededIngredients);
                List<PredicateCollection<MCTownItem, MCTownItem>> neededTools = needs.
                        entrySet().
                        stream().
                        filter(v -> v.getValue().apply(() -> null).value()).
                        map(v -> tools.get(v.getKey())).
                        filter(Objects::nonNull).
                        toList();
                b.put("relevant tools", neededTools);

                List<ContainerTarget<MCContainer, MCTownItem>> containers = TownContainers.getAllContainers(
                        rooms,
                        level
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
                        Optional<?> matchedIngredient = neededIngredients.
                                stream().
                                filter(ing -> ing.test(iHeld)).
                                findFirst();
                        String result = matchedIngredient.map(Object::toString).orElse("No match");
                        b2.put(dPos, new UtilClean.Pair<>(result, c.toShortString(false)));
                        if (matchedIngredient.isPresent()) {
                            found = WithReason.always(true, i.getShortName() + " matches " + matchedIngredient.get());
                            if (stopOnTrue) {
                                break;
                            }
                        }
                        Optional<?> matchedTool = neededTools.
                                stream().
                                filter(ing -> ing.test(i)).
                                findFirst();
                        result = matchedTool.map(Object::toString).orElse("No match");
                        b2.put(dPos, new UtilClean.Pair<>(result, c.toShortString(false)));
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
                return new LZCD.Populated<>(
                        "town has supplies",
                        found,
                        b.build(),
                        null
                );
            }
        };
    }

    private record HandlerInputs(
            MCTownStateWorldInteraction wi,
            MCTownStateWorldInteraction.Inputs inState,
            ProductionStatus status,
            State workBlockState,
            Integer maxState,
            BlockPos fakePos
    ) {
    }

    public static void staticInitialize() {
        ImmutableMap.Builder<ProductionStatus, Function<
                HandlerInputs,
                @Nullable MCTownState
                >> b = ImmutableMap.builder();
        Function<HandlerInputs, @Nullable MCTownState> tryWorking = ii -> {
            @Nullable WorkOutput<MCTownState, WorkPosition<BlockPos>> v = ii.wi.tryWorking(
                    ii.inState, new WorkPosition<>(ii.fakePos, ii.fakePos)
            );
            if (v == null) {
                return null;
            }
            return v.town();
        };

        for (int i = 0; i < ProductionStatus.firstNonCustomIndex; i++) {
            b.put(ProductionStatus.fromJobBlockStatus(i), (
                    HandlerInputs ii
            ) -> {
                if (!ii.status.isWorkingOnProduction()) {
                    return ii.inState.town();
                }
                return tryWorking.apply(ii);
            });
        }
        b.put(
                ProductionStatus.EXTRACTING_PRODUCT,
                tryWorking
        );
        b.put(
                ProductionStatus.DROPPING_LOOT,
                i -> i.wi.simulateDropLoot(i.inState.town(), i.status)
        );
        b.put(
                ProductionStatus.COLLECTING_SUPPLIES,
                i -> i.wi.simulateCollectSupplies(i.inState.town(), i.workBlockState.processingState())
        );
        b.put(
                ProductionStatus.RELAXING,
                i -> null
        );
        b.put(
                ProductionStatus.WAITING_FOR_TIMED_STATE,
                i -> null
        );
        b.put(
                ProductionStatus.NO_SPACE,
                i -> null
        );
        b.put(
                ProductionStatus.GOING_TO_JOB,
                i -> null
        );
        b.put(
                ProductionStatus.NO_SUPPLIES,
                i -> null
        );
        b.put(
                ProductionStatus.IDLE,
                i -> null
        );
        b.put(
                ProductionStatus.NO_JOBSITE,
                i -> null
        );
        handler = b.build();
    }

    public static Warper<ServerLevel, MCTownState> warper(
            MCTownStateWorldInteraction wi,
            int maxState,
            boolean prioritizeExtraction
    ) {
        ImmutableSet<ProductionStatus> c = handler.keySet();
        ImmutableSet<ProductionStatus> productionStatuses = ProductionStatus.allStatuses();
        if (!c.containsAll(productionStatuses)) {
            throw new IllegalStateException("Not all production states are handled. Difference: " + Sets.difference(
                    ImmutableSet.copyOf(productionStatuses), ImmutableSet.copyOf(c)
            ));
        }

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

                MCTownState outState = inState;

                State state = outState.workStates.get(fakePos);
                if (state == null) {
                    outState = outState.setJobBlockState(fakePos, State.fresh());
                }

                ProductionStatus status = ProductionStatus.FACTORY.idle();

                final State ztate = outState.workStates.get(fakePos);

                final MCTownStateWorldInteraction.Inputs fState = new MCTownStateWorldInteraction.Inputs(
                        outState,
                        level,
                        inState.getVillager(villagerNum).uuid
                );
                wi.injectTicks((int) ticksPassed);
                MCRoom fakeRoom = Spaces.metaRoomAround(fakePos, 1);
                @Nullable ProductionStatus nuStatus = ProductionStatuses.getNewStatusFromSignal(
                        status, Signals.fromDayTime(Util.getDayTime(level)),
                        wi.asInventory(() -> wi.getHeldItems(fState, villagerNum), ztate::processingState),
                        wi.asTownJobs(
                                ztate,
                                new RoomRecipeMatch<>(fakeRoom, new ResourceLocation("fake"), ImmutableList.of()),
                                fakePos,
                                outState.containers
                        ),
                        DeclarativeJobs.alwaysInRoom(fakeRoom),
                        STATUS_FACTORY, prioritizeExtraction
                );
                if (nuStatus != null) {
                    status = nuStatus;
                }
                MCTownState affectedState = handler.get(status).apply(new HandlerInputs(
                        wi, fState, status, ztate, maxState, fakePos
                ));
                if (affectedState != null) {
                    outState = affectedState;
                }

                outState = outState.withTimerReducedBy(fakePos, (int) ticksPassed);

                return outState;
            }

            @Override
            public Collection<Tick> getTicks(
                    long referenceTick,
                    long ticksPassed
            ) {
                ImmutableList.Builder<Tick> b = ImmutableList.builder();

                long start = referenceTick;
                long max = referenceTick + ticksPassed;

                // TODO[WARP]: Factor in timers and "walk time"
                int workInterval = wi.interval * 2; // Doubling as a heuristic to simulate walking
                int stepInterval = Math.max(workInterval, 100); // 100 As a heuristic for walking time
                for (long i = start; i <= max; i += stepInterval) {
                    b.add(new Tick(i, stepInterval));
                }
                return b.build();
            }
        };
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

    private static class RoomStates implements LZCD.Dependency<Void> {

        private static final String NAME = "rooms contain workstate";

        private final Supplier<UtilClean.Pair<Map<BlockPos, Integer>, Map<MCRoom, Collection<Integer>>>> inputs;
        private final String name;
        private final int state;
        private LZCD.Populated<WithReason<Boolean>> value;

        public RoomStates(
                int state,
                Supplier<UtilClean.Pair<Map<BlockPos, Integer>, Map<MCRoom, Collection<Integer>>>> inputs
        ) {
            this.inputs = inputs;
            this.name = NAME + " " + state;
            this.state = state;
        }

        @Override
        public LZCD.Populated<WithReason<@Nullable Boolean>> populate() {
            // TODO[Performance]: Cache?
//            if (value != null) {
//                return value;
//            }
            UtilClean.Pair<Map<BlockPos, Integer>, Map<MCRoom, Collection<Integer>>> v = this.inputs.get();
            Map<BlockPos, Integer> spotStates = v.a();
            Optional<Map.Entry<BlockPos, Integer>> foundSpot = spotStates.entrySet().stream()
                                                                         .filter(z -> Integer.compare(
                                                                                 state,
                                                                                 z.getValue()
                                                                         ) == 0).findFirst();
            WithReason<Boolean> hasSpot = foundSpot.map(
                    zz -> WithReason.always(true, "town has workspot with state at " + foundSpot.get().getKey())
            ).orElse(
                    WithReason.always(false, "no spots found")
            );

            ImmutableMap.Builder<String, Object> css = ImmutableMap.builder();
            spotStates.forEach((k, vv) -> css.put(k.toShortString(), vv));
            ImmutableMap.Builder<String, Object> crs = ImmutableMap.builder();
            v.b().forEach((k, vv) -> crs.put(k.doorPos.getUIString(), vv));

            this.value = new LZCD.Populated<>(name, hasSpot, ImmutableMap.of(
                    "spots", css.build(),
                    "rooms", crs.build()
            ), null);
            return value;
        }

        @Override
        public String describe() {
            return "TODO"; // TODO:
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public WithReason<Boolean> apply(Supplier<Void> voidSupplier) {
            return this.populate().value();
        }
    }
}
