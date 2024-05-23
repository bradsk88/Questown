package ca.bradj.questown.jobs;

import ca.bradj.questown.QT;
import ca.bradj.questown.jobs.declarative.WithReason;
import ca.bradj.questown.jobs.production.IProductionJob;
import ca.bradj.questown.jobs.production.IProductionStatus;
import ca.bradj.questown.mc.Util;
import ca.bradj.roomrecipes.core.Room;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class JobStatuses {

    public static boolean hasItems(
            boolean allowCaching,
            EntityInvStateProvider<?> inventory
    ) {
        if (inventory.hasNonSupplyItems(allowCaching).value) {
            return true;
        }
        return inventory.getSupplyItemStatus()
                        .values()
                        .stream()
                        .anyMatch(Boolean::booleanValue);
    }

    public interface Job<STATUS, SUP_CAT> extends LegacyJob<STATUS, SUP_CAT> {
        ImmutableMap<Integer, StatusSupplier<@Nullable STATUS>> getSupplyUsesKeyedByPriority(Map<SUP_CAT, Boolean> supplyItemStatus);

        ImmutableMap<Integer, StatusSupplier<@Nullable STATUS>> getItemlessWorkKeyedByPriority();

        @Nullable SUP_CAT fromInt(int i);
        @Nullable SUP_CAT convert(STATUS i);
    }

    /**
     * @deprecated Use usualRoutineV2
     */
    public static <STATUS extends IStatus<STATUS>, SUP_CAT> WithReason<STATUS> usualRoutine(
            STATUS currentStatus,
            boolean prioritizeExtraction,
            EntityInvStateProvider<SUP_CAT> inventory,
            TownStateProvider town,
            LegacyJob<STATUS, SUP_CAT> job,
            IStatusFactory<STATUS> factory
    ) {
        WithReason<STATUS> v = usualRoutineV2(
                currentStatus,
                prioritizeExtraction,
                inventory,
                town,
                new Job<STATUS, SUP_CAT>() {
                    @Override
                    public ImmutableMap<Integer, StatusSupplier<STATUS>> getSupplyUsesKeyedByPriority(Map<SUP_CAT, Boolean> supplyItemStatus) {
                        return ImmutableMap.of(
                                0, job.tryUsingSupplies(supplyItemStatus)
                        );
                    }

                    @Override
                    public ImmutableMap<Integer, StatusSupplier<STATUS>> getItemlessWorkKeyedByPriority() {
                        return ImmutableMap.of(
                                1, job.tryChoosingItemlessWork()
                        );
                    }

                    @Override
                    public SUP_CAT fromInt(int i) {
                        return null;
                    }

                    @Override
                    public @Nullable SUP_CAT convert(STATUS i) {
                        return null;
                    }

                    @Override
                    public @Nullable StatusSupplier<STATUS> tryChoosingItemlessWork() {
                        return job.tryChoosingItemlessWork();
                    }

                    @Override
                    public StatusSupplier<STATUS> tryUsingSupplies(Map<SUP_CAT, Boolean> supplyItemStatus) {
                        return job.tryUsingSupplies(supplyItemStatus);
                    }
                },
                factory
        );

        return v;
    }

    public static <STATUS extends IStatus<STATUS>, SUP_CAT> WithReason<STATUS> usualRoutineV2(
            STATUS currentStatus,
            // TODO: Phase out in favour of preferences
            boolean prioritizeExtraction,
            EntityInvStateProvider<SUP_CAT> inventory,
            TownStateProvider town,
            Job<STATUS, SUP_CAT> job,
            IStatusFactory<STATUS> factory
    ) {
        Map<SUP_CAT, Boolean> supplyItemStatus = inventory.getSupplyItemStatus();
        boolean hasWorkItems = supplyItemStatus.containsValue(true);
        Map<Integer, StatusSupplier<STATUS>> workToTry = new HashMap<>();
        if (hasWorkItems) {
            workToTry.putAll(job.getSupplyUsesKeyedByPriority(supplyItemStatus));
        }
        putAllOrFallback(workToTry, job.getItemlessWorkKeyedByPriority());

        @Nullable WithReason<STATUS> normalStatus = null;
        boolean foundExtraction = false;

        boolean canGo = false;

        for (int i = 0; i < 10; i++) { // TODO: Smarter range
            StatusSupplier<STATUS> potentialWork = workToTry.get(i);
            if (potentialWork == null) {
                continue;
            }
            WithReason<STATUS> successfulChoice = potentialWork.actualStatus().get();
            if (successfulChoice == null) {
                boolean hasSupplies = Util.getOrDefault(supplyItemStatus, job.convert(potentialWork.targetStatus()), true);
                if (!hasSupplies && town.hasSupplies(i) && town.canUseMoreSupplies(i)) {
                    normalStatus = new WithReason<>(factory.collectingSupplies(), String.format(
                            "Need supplies and town has supplies for target status %s", potentialWork.targetStatus()
                    ));
                }
                continue;
            }
            if (WithReason.isSame(factory.extractingProduct(), successfulChoice)) {
                foundExtraction = true;
            }
            if (normalStatus == null) {
                if (WithReason.isSame(factory.goingToJobSite(), successfulChoice)) {
                    canGo = true;
                } else {
                    normalStatus = successfulChoice;
                }
            }
        }

        if (foundExtraction && prioritizeExtraction) {
            return nullIfUnchanged(currentStatus, new WithReason<>(factory.extractingProduct(), "Found extraction and prioritized"));
        } else if (normalStatus != null) {
            return nullIfUnchanged(currentStatus, normalStatus.wrap("Job-block-based status found"));
        } else if (canGo) {
            return nullIfUnchanged(currentStatus, new WithReason<>(factory.goingToJobSite(), "Job-block-based work found elsewhere"));
        }

        WithReason<STATUS> s = null;
        boolean hasItems = hasWorkItems || inventory.hasNonSupplyItems(town.isCachingAllowed()).value;
        if (inventory.inventoryFull()) {
            if (town.hasSpace()) {
                s = new WithReason<>(factory.droppingLoot(), "Inventory is full and a place exists to put items");
            } else {
                s = new WithReason<>(factory.noSpace(), "Inventory is full but there is nowhere to put them");
            }
        }

        if (s != null) {
            s = nullIfUnchanged(currentStatus, s);
            if (s != null && s.value() != factory.goingToJobSite()) {
                return s;
            } else if (inventory.inventoryFull() || (hasItems && !town.hasSupplies())) {
                return s;
            }
        }

        WithReason<STATUS> s2 = s;
        if (s2 != null) {
            return nullIfUnchanged(currentStatus, s2);
        } else {
            WithReason<Boolean> hasNS = inventory.hasNonSupplyItems(town.isCachingAllowed());
            if (hasNS.value) {
                if (town.hasSpace()) {
                    s2 = new WithReason<>(factory.droppingLoot(), "There are non-work items in the inventory and space exists in town for them (%s)", hasNS.reason);
                } else {
                    s2 = new WithReason<>(factory.noSpace(), "There are non-work items in the inventory but town is full");
                }
            } else if (!town.hasSupplies()) {
                if (town.canUseMoreSupplies()) {
                    s2 = nullIfUnchanged(currentStatus, new WithReason<>(factory.noSupplies(), "Work can be done, but no ingredients are present in town"));
                } else if (hasItems) {
                    s2 = nullIfUnchanged(currentStatus, new WithReason<>(factory.droppingLoot(), "There is no work to do, the villager has items, and there is space in town"));
                } else {
                    s2 = new WithReason<>(factory.noJobSite(), "There is no work to do, and nothing in the inventory");
                }
            } else {
                if (hasItems && !hasWorkItems) {
                    s2 = nullIfUnchanged(currentStatus, new WithReason<>(factory.droppingLoot(), "Villager has items that are not for work"));
                } else if (town.canUseMoreSupplies()) {
                    s2 = nullIfUnchanged(currentStatus, new WithReason<>(factory.collectingSupplies(), "Work can be done, and there are supplies"));
                } else {
                    if (town.isTimerActive()) {
                        return new WithReason<>(factory.waitingForTimedState(), "There are timers active");
                    }
                    return new WithReason<>(factory.noJobSite(), "There are supplies in town, but no work to do");
                }
            }
        }

        if ((s2 != null && s2.value() != factory.collectingSupplies()) && s != null) {
            return s;
        }

        return s2;
    }

    private static <STATUS extends IStatus<STATUS>> void putAllOrFallback(
            Map<Integer, StatusSupplier<STATUS>> workToTry,
            ImmutableMap<Integer, StatusSupplier<STATUS>> itemlessWorkKeyedByPriority
    ) {
        itemlessWorkKeyedByPriority.keySet().forEach(
                key -> workToTry.compute(key, (k, cur) -> {
                    StatusSupplier<STATUS> il = itemlessWorkKeyedByPriority.get(k);
                    if (il != null) {
                        @Nullable WithReason<STATUS> status = il.actualStatus().get();
                        if (status != null) {
                            return il;
                        }
                    }
                    return cur;
                })
        );
    }

    /**
     * A standard daily routine for mobs who insert materials into a block in
     * their job-related room(s) and then remove a product from the room once
     * it's ready.
     *
     * @param prioritizeExtraction: If set to true, the entity will remove
     *                              finished products from blocks even if they
     *                              don't have space in their inventory. This
     *                              will cause the item to be spawned into the
     *                              world for collection by whomever walks by.
     */
    public static <STATUS extends IProductionStatus<STATUS>, ROOM extends Room> WithReason<STATUS> productionRoutine(
            STATUS currentStatus,
            boolean prioritizeExtraction,
            EntityInvStateProvider<STATUS> inventory,
            EntityLocStateProvider<ROOM> entity,
            JobTownProvider<ROOM, STATUS> town,
            IProductionJob<STATUS> job,
            IProductionStatusFactory<STATUS> factory,
            STATUS maxState
    ) {
        if (factory.waitingForTimedState()
                   .equals(currentStatus)) {
            if (town.isUnfinishedTimeWorkPresent()) {
                return null;
            }
        }
        WithReason<STATUS> status = usualRoutineV2(
                currentStatus, prioritizeExtraction, inventory,
                new TownStateProvider() {
                    @Override
                    public boolean hasSupplies() {
                        return town.hasSupplies();
                    }

                    @Override
                    public boolean hasSupplies(int i) {
                        return town.hasSupplies(i);
                    }

                    @Override
                    public boolean hasSpace() {
                        return town.hasSpace();
                    }

                    @Override
                    public boolean isTimerActive() {
                        return town.isUnfinishedTimeWorkPresent();
                    }

                    @Override
                    public boolean isCachingAllowed() {
                        return town.isCachingAllowed();
                    }

                    @Override
                    public boolean canUseMoreSupplies() {
                        return !town.roomsToGetSuppliesForByState()
                                    .entrySet()
                                    .stream()
                                    .allMatch(v -> v.getValue()
                                                    .isEmpty());
                    }

                    @Override
                    public boolean canUseMoreSupplies(int i) {
                        return !Util.getOrDefault(
                                town.roomsToGetSuppliesForByState(), factory.fromJobBlockState(i), ImmutableList.of()
                        ).isEmpty();
                    }
                },
                new Job<STATUS, STATUS>() {
                    @Override
                    public @Nullable StatusSupplier<STATUS> tryChoosingItemlessWork() {
                        Collection<Integer> states = town.getStatesWithUnfinishedItemlessWork();
                        for (Integer state : states) {
                            return new StatusSupplier<>(fromInt(state), () -> new WithReason<>(
                                    factory.fromJobBlockState(state),
                                    String.format("Town lists status %d as having itemless work to do", state)
                            ));
                        }

                        Collection<ROOM> rooms = town.roomsWithCompletedProduct();
                        if (rooms.isEmpty()) {
                            return null;
                        }

                        ROOM location = entity.getEntityCurrentJobSite();
                        if (location != null) {
                            if (rooms.contains(location)) {
                                return new StatusSupplier<>(maxState, () -> new WithReason<>(
                                        factory.extractingProduct(),
                                        String.format("Current room has product: %s", location)
                                ));
                            }
                        }

                        return new StatusSupplier<>(maxState, () -> new WithReason<>(
                                factory.extractingProduct(),
                                String.format("A room has product: %s", location)
                        ));
                    }

                    @Override
                    public ImmutableMap<Integer, StatusSupplier<STATUS>> getItemlessWorkKeyedByPriority() {
                        Collection<Integer> workReadyToDo = town.getStatesWithUnfinishedItemlessWork();
                        ImmutableList<STATUS> allByPref = job.getAllWorkStatesSortedByPreference();
                        Map<Integer, StatusSupplier<STATUS>> b = new HashMap<>();
                        for (int i = 0; i < allByPref.size(); i++) {
                            STATUS potentialStatus = allByPref.get(i);
                            if (workReadyToDo.contains(potentialStatus.value())) {
                                b.put(i, new StatusSupplier<>(potentialStatus, () -> JobsClean.doOrGoTo(
                                        potentialStatus,
                                        entity.getEntityCurrentJobSite() != null,
                                        factory.goingToJobSite()
                                )));
                                break;
                            }
                        }

                        b.compute(job.getMaxState().value(), (idx, cur) -> new StatusSupplier<>(job.getMaxState(), () -> {
                            if (cur != null) {
                                return cur.actualStatus().get();
                            }

                            Collection<ROOM> rooms = town.roomsWithCompletedProduct();
                            if (rooms.isEmpty()) {
                                return null;
                            }
                            return JobsClean.doOrGoTo(
                                    factory.extractingProduct(),
                                    entity.getEntityCurrentJobSite() != null,
                                    factory.goingToJobSite()
                            );
                        }));

                        return ImmutableMap.copyOf(b);
                    }

                    @Override
                    public @Nullable STATUS fromInt(int i) {
                        return factory.fromJobBlockStateOrNull(i);
                    }

                    @Override
                    public @Nullable STATUS convert(STATUS i) {
                        return i;
                    }

                    @Override
                    public @Nullable StatusSupplier<STATUS> tryUsingSupplies(Map<STATUS, Boolean> supplyItemStatus) {
                        if (supplyItemStatus.isEmpty()) {
                            return null;
                        }
                        ROOM location = entity.getEntityCurrentJobSite();
                        ImmutableMap<STATUS, ImmutableList<Room>> roomNeedsMap = town.roomsToGetSuppliesForByState();

                        STATUS foundWork = null;

                        List<STATUS> orderedWithSupplies = job.getAllWorkStatesSortedByPreference()
                                                              .stream()
                                                              .filter(work -> supplyItemStatus.getOrDefault(
                                                                      work, false))
                                                              .toList();

                        for (STATUS s : orderedWithSupplies) {
                            if (roomNeedsMap.containsKey(s) && !roomNeedsMap.get(s)
                                                                            .isEmpty()) { // TODO: Unit test the second leg of this condition
                                foundWork = s;
                                if (location != null) {
                                    if (roomNeedsMap.get(s).contains(location)) {
                                        return StatusSupplier.found(s, "The current room needs the items that are being held");
                                    }
                                }
                            }
                        }

                        if (foundWork != null) {
                            return new StatusSupplier<>(foundWork, () -> new WithReason<>(
                                    factory.goingToJobSite(),
                                    "Located work in a different room"
                            ));
                        }
                        // TODO: Return null here. This call to `try` might be needed for the farmer job.
                        //  Let's convert that into a production job.
                        return job.tryUsingSupplies(supplyItemStatus);
                    }

                    @Override
                    public ImmutableMap<Integer, StatusSupplier<STATUS>> getSupplyUsesKeyedByPriority(Map<STATUS, Boolean> supplyItemStatus) {
                        if (supplyItemStatus.isEmpty()) {
                            return null;
                        }
                        ROOM location = entity.getEntityCurrentJobSite();
                        final ImmutableMap<STATUS, ImmutableList<Room>> roomNeedsMap = town.roomsToGetSuppliesForByState();

                        ImmutableList<STATUS> allByPref = job.getAllWorkStatesSortedByPreference();
                        ImmutableMap.Builder<Integer, StatusSupplier<STATUS>> b = ImmutableMap.builder();
                        for (int i = 0; i < allByPref.size(); i++) {
                            final STATUS s = allByPref.get(i);
                            boolean hasSuppliesForStatus = Util.getOrDefault(supplyItemStatus, s, false);
                            if (hasSuppliesForStatus) {
                                b.put(i, new StatusSupplier<>(s, () -> {
                                    if (roomNeedsMap.containsKey(s) && !roomNeedsMap.get(s)
                                                                                    .isEmpty()) { // TODO: Unit test the second leg of this condition
                                        if (location != null) {
                                            if (roomNeedsMap.get(s)
                                                            .contains(location)) {
                                                return new WithReason<>(s, "Supplies can be used in the current room");
                                            }
                                        }
                                        return new WithReason<>(factory.goingToJobSite(), "Supplies can be used in a different room");
                                    }
                                    return null;
                                }));
                            }
                        }
                        return b.build();
                    }
                }, factory
        );
        // TODO: For "no supplies" status, ignore rooms that only need tools
        // Because rooms needing tools "need supplies" at all times, the logic chooses that status.
        if (isDoNothingStatus(factory, status)) {
            if (town.isUnfinishedTimeWorkPresent()) {
                status = nullIfUnchanged(currentStatus, new WithReason<>(factory.waitingForTimedState(), "Town has time work"));
            }
        }

        if (status != null) {
            QT.JOB_LOGGER.debug("Status changed to {} because {}", status.value(), status.reason());
        }

        return status;
    }

    private static <STATUS extends IProductionStatus<STATUS>> boolean isDoNothingStatus(
            IProductionStatusFactory<STATUS> factory,
            WithReason<STATUS> status
    ) {
        return status == null || factory.idle()
                                        .equals(status.value()) || factory.noSupplies()
                                                                  .equals(status.value());
    }

    private static <S> @Nullable WithReason<S> nullIfUnchanged(
            S oldStatus,
            WithReason<S> newStatus
    ) {
        if (oldStatus == null && newStatus == null) {
            return null;
        }
        if (oldStatus == newStatus.value()) {
            return null;
        }
        return newStatus;
    }
}

