package ca.bradj.questown.jobs;

import ca.bradj.questown.jobs.production.ProductionStatus;
import ca.bradj.questown.jobs.production.ProductionStatuses;
import ca.bradj.questown.town.Warper;
import ca.bradj.questown.town.workstatus.State;
import ca.bradj.roomrecipes.core.Room;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;

import javax.annotation.Nullable;
import java.util.function.Function;

public abstract class AbstractDeclarativeJobWarper<TOWN, ROOM extends Room, POS, LEVEL> {

    /**
     * Functional interface for computing the next ProductionStatus.
     * Inject a custom implementation for testing.
     */
    @FunctionalInterface
    public interface StatusProvider<ROOM extends Room> {
        @Nullable ProductionStatus computeStatus(
                ProductionStatus currentStatus,
                Signals signal,
                EntityInvStateProvider<Integer> inventory,
                JobTownProvider<ROOM> town,
                EntityLocStateProvider<ROOM> entityLocation,
                boolean prioritizeExtraction
        );
    }

    private final StatusProvider<ROOM> statusProvider;

    /**
     * Default constructor uses ProductionStatuses.getNewStatusFromSignal
     */
    public AbstractDeclarativeJobWarper() {
        this((status, signal, inv, town, loc, prioritize) ->
                ProductionStatuses.getNewStatusFromSignal(
                        status, signal, inv, town, loc,
                        DeclarativeJobs.STATUS_FACTORY, prioritize
                )
        );
    }

    /**
     * Constructor for dependency injection (useful for testing)
     */
    public AbstractDeclarativeJobWarper(StatusProvider<ROOM> statusProvider) {
        this.statusProvider = statusProvider;
    }

    private static final Hendlar NULL_HENDLAR = new Hendlar() {
        @Override
        public <TOWN, POS, LEVEL> TOWN hendle(HendlarInpoots<TOWN, POS, LEVEL> inputs) {
            return null;
        }
    };
    private static ImmutableMap<ProductionStatus, Hendlar> handler;

    private static <TOWN, POS, LEVEL> TOWN tryWorking(
            HendlarInpoots<TOWN, POS, LEVEL> ii
    ) {
        @org.jetbrains.annotations.Nullable WorkOutput<TOWN, WorkPosition<POS>> v = ii.wi().tryWorking(
                ii.inState(),
                new WorkPosition<>(ii.workSpotStandIn(), ii.workSpotStandIn())
        );
        if (v == null) {
            return null;
        }
        return v.town();
    }

    private static <TOWN, POS, LEVEL> TOWN tryWorkingProduction(HendlarInpoots<TOWN, POS, LEVEL> ii) {
        if (!ii.status().isWorkingOnProduction()) {
            return ii.inState().town();
        }
        return tryWorking(ii);
    }

    private static <TOWN> TOWN dropLoot(HendlarInpoots<TOWN, ?, ?> ii) {
        return ii.wi().simulateDropLoot(ii.inState().town(), ii.status());
    }


    private static <TOWN> TOWN collectSupplies(HendlarInpoots<TOWN, ?, ?> ii) {
        return ii.wi().simulateCollectSupplies(ii.inState().town(), ii.workBlockState().processingState());
    }

    public static void staticInitialize() {
        ImmutableMap.Builder<ProductionStatus, Hendlar> b = ImmutableMap.builder();

        for (int i = 0; i < ProductionStatus.firstNonCustomIndex; i++) {
            b.put(ProductionStatus.fromJobBlockStatus(i), AbstractDeclarativeJobWarper::tryWorkingProduction);
        }
        b.put(ProductionStatus.EXTRACTING_PRODUCT, AbstractDeclarativeJobWarper::tryWorking);
        b.put(ProductionStatus.DROPPING_LOOT, AbstractDeclarativeJobWarper::dropLoot);
        b.put(ProductionStatus.COLLECTING_SUPPLIES, AbstractDeclarativeJobWarper::collectSupplies);
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
            throw new IllegalStateException("Not all production states are handled. Difference: " + Sets.difference(
                    ImmutableSet.copyOf(productionStatuses),
                    ImmutableSet.copyOf(c)
            ));
        }
    }

    public interface WorkSpotStandIn<TOWN, POS> {

        POS get();

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

    public TOWN warp(
            WorkSpotStandIn<TOWN, POS> workspot,
            TOWN inState,
            Warper.Tick tick,
            Function<TOWN, EntityInvStateProvider<Integer>> entityInventory,
            Function<TOWN, JobTownProvider<ROOM>> town,
            EntityLocStateProvider<ROOM> entityLocation,
            boolean prioritizeExtraction,
            AbstractStateInteraction<Inpoots<TOWN, LEVEL>, POS, ?, ?, TOWN> wi,
            Function<TOWN, Inpoots<TOWN, LEVEL>> inpoooots,
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
        @Nullable ProductionStatus nuStatus = statusProvider.computeStatus(
                status,
                Signals.fromDayTime(Signals.DayTime.fromGameTime(tick.tick())),
                entityInventory.apply(outState),
                town.apply(outState),
                entityLocation,
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
        TOWN affectedState = handler.get(status).hendle(new HendlarInpoots<>(
                wi,
                inpoooots.apply(outState),
                status,
                workspot.getState(outState),
                maxState,
                workspot.get()
        ));
        if (affectedState != null) {
            outState = affectedState;
        }

        outState = workspot.withTimerReducedBy(outState, (int) tick.ticksSincePrevious());

        return outState;
    }
}
