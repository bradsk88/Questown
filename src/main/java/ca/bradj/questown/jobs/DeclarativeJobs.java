package ca.bradj.questown.jobs;

import ca.bradj.questown.integration.minecraft.MCTownState;
import ca.bradj.questown.jobs.declarative.WithReason;
import ca.bradj.questown.jobs.production.ProductionStatus;
import ca.bradj.questown.jobs.production.ProductionStatuses;
import ca.bradj.questown.mc.Util;
import ca.bradj.questown.roomrecipes.Spaces;
import ca.bradj.questown.town.AbstractWorkStatusStore;
import ca.bradj.questown.town.Warper;
import ca.bradj.roomrecipes.serialization.MCRoom;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.BiPredicate;
import java.util.function.Function;

public class DeclarativeJobs {
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

    private static final ImmutableMap<ProductionStatus, Function<HandlerInputs, MCTownState>> handler;

    private record HandlerInputs(
            MCTownStateWorldInteraction wi,
            MCTownStateWorldInteraction.Inputs inState,
            ProductionStatus status,
            AbstractWorkStatusStore.State workBlockState,
            Integer maxState,
            BlockPos fakePos
    ) {
    }

    // FIXME: Use staticInitialize() and confirm all production statuses are defined
    static {
        ImmutableMap.Builder<ProductionStatus, Function<
                HandlerInputs,
                @Nullable MCTownState
                >> b = ImmutableMap.builder();
        Function<HandlerInputs, @Nullable MCTownState> tryWorking = ii -> {
            WithReason<@Nullable WorkOutput<MCTownState, WorkSpot<Integer, BlockPos>>> v = ii.wi.tryWorking(
                    ii.inState,
                    new WorkSpot<>(ii.fakePos, ii.workBlockState.processingState(), 1, ii.fakePos)
            );
            if (v.value() == null) {
                return null;
            }
            return v.value().town();
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
            boolean prioritizeExtraction,
            ImmutableList<ProductionStatus> statePriority
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

                AbstractWorkStatusStore.State state = outState.workStates.get(fakePos);
                if (state == null) {
                    outState = outState.setJobBlockState(fakePos, AbstractWorkStatusStore.State.fresh());
                }

                ProductionStatus status = ProductionStatus.FACTORY.idle();

                final AbstractWorkStatusStore.State ztate = outState.workStates.get(fakePos);

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
                                fakeRoom,
                                fakePos,
                                outState.containers
                        ),
                        DeclarativeJobs.alwaysInRoom(fakeRoom),
                        DeclarativeJob.STATUS_FACTORY, prioritizeExtraction,
                        statePriority
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

                // TODO[ASAP]: Factor in timers and "walk time"
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
}
