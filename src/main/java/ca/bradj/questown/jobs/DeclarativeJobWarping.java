package ca.bradj.questown.jobs;

import ca.bradj.questown.jobs.production.ProductionStatus;
import ca.bradj.questown.jobs.production.ProductionStatuses;
import ca.bradj.questown.town.Warper;
import ca.bradj.questown.town.rooms.TownPosition;
import ca.bradj.questown.town.workstatus.State;
import ca.bradj.roomrecipes.core.Room;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;

import javax.annotation.Nullable;
import java.util.function.Function;

public class DeclarativeJobWarping {

    private static final Hendlar NULL_HENDLAR = new Hendlar() {
        @Override
        public <TOWN> TOWN hendle(HendlarInpoots<TOWN> inputs) {
            return null;
        }
    };
    private static ImmutableMap<ProductionStatus, Hendlar> handler;

    private static <TOWN> TOWN tryWorking(
            HendlarInpoots<TOWN> ii
    ) {
        TownPosition fakePos = new TownPosition(ii.villagerIndex(), ii.villagerIndex(), ii.villagerIndex());
        @org.jetbrains.annotations.Nullable WorkOutput<TOWN, WorkPosition<TownPosition>> v = ii.wi().tryWorking(
                ii.inState(),
                new WorkPosition<>(fakePos, fakePos)
        );
        if (v == null) {
            return null;
        }
        return v.town();
    }

    private static <TOWN> TOWN tryWorkingProduction(HendlarInpoots<TOWN> ii) {
        if (!ii.status().isWorkingOnProduction()) {
            return ii.inState().town();
        }
        return tryWorking(ii);
    }

    private static <TOWN> TOWN dropLoot(HendlarInpoots<TOWN> ii) {
        return ii.wi().simulateDropLoot(ii.inState().town(), ii.status());
    }


    private static <TOWN> TOWN collectSupplies(HendlarInpoots<TOWN> ii) {
        return ii.wi().simulateCollectSupplies(ii.inState().town(), ii.workBlockState().processingState());
    }

    public static void staticInitialize() {
        ImmutableMap.Builder<ProductionStatus, Hendlar> b = ImmutableMap.builder();

        for (int i = 0; i < ProductionStatus.firstNonCustomIndex; i++) {
            b.put(ProductionStatus.fromJobBlockStatus(i), DeclarativeJobWarping::tryWorkingProduction);
        }
        b.put(ProductionStatus.EXTRACTING_PRODUCT, DeclarativeJobWarping::tryWorking);
        b.put(ProductionStatus.DROPPING_LOOT, DeclarativeJobWarping::dropLoot);
        b.put(ProductionStatus.COLLECTING_SUPPLIES, DeclarativeJobWarping::collectSupplies);
        b.put(ProductionStatus.RELAXING, NULL_HENDLAR);
        b.put(ProductionStatus.WAITING_FOR_TIMED_STATE, NULL_HENDLAR);
        b.put(ProductionStatus.NO_SPACE, NULL_HENDLAR);
        b.put(ProductionStatus.GOING_TO_JOB, NULL_HENDLAR);
        b.put(ProductionStatus.NO_SUPPLIES, NULL_HENDLAR);
        b.put(ProductionStatus.IDLE, NULL_HENDLAR);
        b.put(ProductionStatus.NO_JOBSITE, NULL_HENDLAR);
        handler = b.build();
    }

    public static void sanityCheck() {
        ImmutableSet<ProductionStatus> c = handler.keySet();
        ImmutableSet<ProductionStatus> productionStatuses = ProductionStatus.allStatuses();
        if (!c.containsAll(productionStatuses)) {
            throw new IllegalStateException("Not all production states are handled. Difference: " + Sets.difference(ImmutableSet.copyOf(productionStatuses),
                    ImmutableSet.copyOf(c)
            ));
        }
    }

    public interface WorkSpotStandIn<TOWN> {

        State getState(TOWN town);

        TOWN setState(
                TOWN outState,
                State newValue
        );

        TOWN withTimerReducedBy(
                TOWN outState,
                int ticksPassed
        );
    }

    public static <TOWN, ROOM extends Room> TOWN warp(
            int villagerIndex,
            WorkSpotStandIn<TOWN> workspot,
            TOWN inState,
            Warper.Tick tick,
            Function<TOWN, EntityInvStateProvider<Integer>> entityInventory,
            Function<TOWN, JobTownProvider<ROOM>> town,
            EntityLocStateProvider<ROOM> entityLocation,
            boolean prioritizeExtraction,
            AbstractStateInteraction<Inpoots<TOWN>, TownPosition, ?, ?, TOWN> wi,
            Function<TOWN, Inpoots<TOWN>> inpoooots,
            int maxState
    ) {

//        BlockPos fakePos = new BlockPos(villagerNum, villagerNum, villagerNum);
//
        TOWN outState = inState;
//
        State state = workspot.getState(outState);
        if (state == null) {
            outState = workspot.setState(outState, State.fresh());
        }
//
//
//        final State ztate = workspot.getState(outState);
//
//        final MCTownStateWorldInteraction.Inputs fState = new MCTownStateWorldInteraction.Inputs(
//                outState,
//                level,
//                inState.getVillager(villagerNum).uuid
//        );
//        wi.injectTicks((int) ticksPassed);
//        MCRoom fakeRoom = Spaces.metaRoomAround(fakePos, 1);
        ProductionStatus status = ProductionStatus.FACTORY.idle();
        @Nullable ProductionStatus nuStatus = ProductionStatuses.getNewStatusFromSignal(
                status,
                Signals.fromDayTime(Signals.DayTime.fromGameTime(tick.tick())),
                entityInventory.apply(outState),
                town.apply(outState),
                entityLocation,
                DeclarativeJobs.STATUS_FACTORY,
                prioritizeExtraction
        );
//        @Nullable ProductionStatus nuStatus = ProductionStatuses.getNewStatusFromSignal(
//                status,
//                Signals.fromDayTime(Signals.DayTime.fromGameTime(tick.tick())),
//                entityInventory,
//                wi.asTownJobs(
//                        ztate,
//                        new RoomRecipeMatch<>(
//                                fakeRoom,
//                                ImmutableList.of(new ResourceLocation("fake")),
//                                ImmutableList.of()
//                        ),
//                        fakePos,
//                        outState.containers
//                ),
//                ,
//                STATUS_FACTORY,
//                prioritizeExtraction
//        );
        if (nuStatus != null) {
            status = nuStatus;
        }

        // TODO[Warp]: Test this part
        TOWN affectedState = handler.get(status).hendle(new HendlarInpoots<TOWN>(
                wi,
                inpoooots.apply(outState),
                status,
                workspot.getState(outState),
                maxState,
                villagerIndex
        ));
        if (affectedState != null) {
            outState = affectedState;
        }

        outState = workspot.withTimerReducedBy(outState, (int) tick.ticksSincePrevious());

        return outState;
    }
}
