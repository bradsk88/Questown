package ca.bradj.questown.jobs;

import ca.bradj.questown.QT;
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

    // Virtual morning time for warp status computation.
    // During warp, we use this instead of actual game time to ensure villagers
    // are productive (not relaxing due to nighttime).
    // 1000 ticks = ~1am in Minecraft time, plenty of daytime ahead.
    private static final long VIRTUAL_MORNING_TICK = 1000;

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
    private final boolean strictMode;

    /**
     * Default constructor uses ProductionStatuses.getNewStatusFromSignal
     */
    public AbstractDeclarativeJobWarper() {
        this((status, signal, inv, town, loc, prioritize) ->
                ProductionStatuses.getNewStatusFromSignal(
                        status, signal, inv, town, loc,
                        DeclarativeJobs.STATUS_FACTORY, prioritize
                ),
                false
        );
    }

    /**
     * Constructor for dependency injection (useful for testing)
     */
    public AbstractDeclarativeJobWarper(StatusProvider<ROOM> statusProvider) {
        this(statusProvider, false);
    }

    /**
     * Constructor with strict mode flag.
     * When strictMode is true, throws on invalid statuses like GOING_TO_JOB
     * (since the warper assumes the worker is already at the jobsite).
     */
    public AbstractDeclarativeJobWarper(StatusProvider<ROOM> statusProvider, boolean strictMode) {
        this.statusProvider = statusProvider;
        this.strictMode = strictMode;
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
        b.put(ProductionStatus.NO_WORK_POSSIBLE, NULL_HENDLAR);
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
            int maxState,
            @Nullable ProductionStatus initialStatus
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
        // Use the villager's current status if provided, otherwise start fresh
        ProductionStatus status = initialStatus != null ? initialStatus : ProductionStatus.FACTORY.idle();

        // If the villager is in WAITING_FOR_TIMED_STATE (e.g., gatherer out gathering),
        // they need to complete their gathering trip before starting new cycles.
        //
        // Strategy: compute the normal status first. If the status would be
        // COLLECTING_SUPPLIES, that means we're starting a new cycle and should
        // let it proceed (with food consumption). Otherwise, if villager has no items
        // and journal says WAITING_FOR_TIMED_STATE, force extraction.
        EntityInvStateProvider<Integer> inventory = entityInventory.apply(outState);
        boolean villagerHasNonSupplyItems = inventory.hasNonSupplyItems();
        // Also check for supply items (like food) - if villager has food, they've already
        // collected supplies and should proceed to consume them, not force extraction.
        boolean villagerHasSupplyItems = inventory.getSupplyItemStatus().values().stream()
                .anyMatch(supplyStatus -> supplyStatus == SupplyItemStatus.HAS_ITEM);

        // Always compute the normal status first
        @Nullable ProductionStatus computedStatus = statusProvider.computeStatus(
                status,
                Signals.fromDayTime(Signals.DayTime.fromGameTime(VIRTUAL_MORNING_TICK)),
                inventory,
                town.apply(outState),
                entityLocation,
                prioritizeExtraction
        );

        // Determine if we need to force extraction:
        // - Journal says WAITING_FOR_TIMED_STATE (villager was out gathering)
        // - No items in inventory (neither loot NOR supplies like food)
        // - Computed status is NOT COLLECTING_SUPPLIES (if it is, we're starting a new cycle)
        boolean wouldCollectSupplies = computedStatus != null && computedStatus.isCollectingSupplies();
        boolean needsFirstExtraction = initialStatus != null
                && initialStatus.isWaitingForTimers()
                && !villagerHasNonSupplyItems
                && !villagerHasSupplyItems
                && !wouldCollectSupplies;

        if (needsFirstExtraction) {
            // Villager was out gathering and needs to extract their loot first.
            status = ProductionStatus.EXTRACTING_PRODUCT;
            outState = workspot.setState(outState, State.fresh().setProcessing(maxState));
        } else if (computedStatus != null) {
            status = computedStatus;
        }

        QT.JOB_LOGGER.debug(
                "[WARP] tick={} status={} workState={}",
                tick.tick(),
                status,
                workspot.getState(outState)
        );

        if (strictMode && status.equals(ProductionStatus.GOING_TO_JOB)) {
            throw new IllegalStateException(
                    "GOING_TO_JOB status during warp is invalid. " +
                    "The warper assumes workers are already at their jobsite."
            );
        }

        Hendlar hendlar = handler.get(status);
        TOWN affectedState = hendlar.hendle(new HendlarInpoots<>(
                wi,
                inpoooots.apply(outState),
                status,
                workspot.getState(outState),
                maxState,
                workspot.get()
        ));
        if (affectedState != null) {
            QT.JOB_LOGGER.debug(
                    "[WARP] handler={} stateChanged=true",
                    hendlar.getClass().getSimpleName()
            );
            outState = affectedState;
        }

        outState = workspot.withTimerReducedBy(outState, (int) tick.ticksSincePrevious());

        return outState;
    }
}
