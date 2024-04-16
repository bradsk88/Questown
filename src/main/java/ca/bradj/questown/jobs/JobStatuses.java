package ca.bradj.questown.jobs;

import ca.bradj.questown.jobs.production.IProductionJob;
import ca.bradj.questown.jobs.production.IProductionStatus;
import ca.bradj.questown.jobs.production.ProductionStatus;
import ca.bradj.questown.mc.Util;
import ca.bradj.roomrecipes.core.Room;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

public class JobStatuses {

    public static boolean hasItems(
            boolean allowCaching,
            EntityInvStateProvider<?> inventory
    ) {
        if (inventory.hasNonSupplyItems(allowCaching)) {
            return true;
        }
        return inventory.getSupplyItemStatus()
                        .values()
                        .stream()
                        .anyMatch(Boolean::booleanValue);
    }

    public interface Job<STATUS, SUP_CAT> extends LegacyJob<STATUS, SUP_CAT> {
        ImmutableMap<Integer, Supplier<@Nullable STATUS>> getSupplyUsesKeyedByPriority(Map<SUP_CAT, Boolean> supplyItemStatus);

        ImmutableMap<Integer, Supplier<@Nullable STATUS>> getItemlessWorkKeyedByPriority();
    }

    /**
     * @deprecated Use usualRoutineV2
     */
    public static <STATUS extends IStatus<STATUS>, SUP_CAT> STATUS usualRoutine(
            STATUS currentStatus,
            boolean prioritizeExtraction,
            EntityInvStateProvider<SUP_CAT> inventory,
            TownStateProvider town,
            LegacyJob<STATUS, SUP_CAT> job,
            IStatusFactory<STATUS> factory
    ) {
        return usualRoutineV2(currentStatus, prioritizeExtraction, inventory, town, new Job<>() {
            @Override
            public ImmutableMap<Integer, Supplier<@Nullable STATUS>> getSupplyUsesKeyedByPriority(Map<SUP_CAT, Boolean> supplyItemStatus) {
                return ImmutableMap.of(
                        0, () -> job.tryUsingSupplies(supplyItemStatus)
                );
            }

            @Override
            public ImmutableMap<Integer, Supplier<@Nullable STATUS>> getItemlessWorkKeyedByPriority() {
                return ImmutableMap.of(
                        1, job::tryChoosingItemlessWork
                );
            }

            @Override
            public @Nullable STATUS tryChoosingItemlessWork() {
                return job.tryChoosingItemlessWork();
            }

            @Override
            public @Nullable STATUS tryUsingSupplies(Map<SUP_CAT, Boolean> supplyItemStatus) {
                return job.tryUsingSupplies(supplyItemStatus);
            }
        }, factory);
    }

    public static <STATUS extends IStatus<STATUS>, SUP_CAT> STATUS usualRoutineV2(
            STATUS currentStatus,
            // TODO: Phase out in favour of preferences
            boolean prioritizeExtraction,
            EntityInvStateProvider<SUP_CAT> inventory,
            TownStateProvider town,
            Job<STATUS, SUP_CAT> job,
            IStatusFactory<STATUS> factory
    ) {
        STATUS s = null;
        Map<SUP_CAT, Boolean> supplyItemStatus = inventory.getSupplyItemStatus();
        boolean hasWorkItems = supplyItemStatus.containsValue(true);
        Map<Integer, Supplier<@Nullable STATUS>> workToTry = new HashMap<>();
        if (hasWorkItems) {
            workToTry.putAll(job.getSupplyUsesKeyedByPriority(supplyItemStatus));
        }
        workToTry.putAll(job.getItemlessWorkKeyedByPriority());

        @Nullable STATUS normalStatus = null;
        boolean foundExtraction = false;
        boolean canGo = false;

        for (int i = 0; i < 10; i++) { // TODO: Smarter range
            Supplier<STATUS> potentialWork = Util.getOrDefault(workToTry, i, () -> null);
            STATUS successfulChoice = potentialWork.get();
            if (successfulChoice != null) {
                if (factory.extractingProduct().equals(successfulChoice)) {
                    foundExtraction = true;
                }
                if (normalStatus == null) {
                    if (factory.goingToJobSite().equals(successfulChoice)) {
                        canGo = true;
                    } else {
                        normalStatus = successfulChoice;
                    }
                }
            }
        }

        if (foundExtraction && prioritizeExtraction) {
            return nullIfUnchanged(currentStatus, factory.extractingProduct());
        } else if (normalStatus != null) {
            return nullIfUnchanged(currentStatus, normalStatus);
        } else if (canGo) {
            return nullIfUnchanged(currentStatus, factory.goingToJobSite());
        }

        boolean hasItems = hasWorkItems || inventory.hasNonSupplyItems(town.isCachingAllowed());
        if (inventory.inventoryFull()) {
            if (town.hasSpace()) {
                s = factory.droppingLoot();
            } else {
                s = factory.noSpace();
            }
        }

        if (s != null) {
            s = nullIfUnchanged(currentStatus, s);
            if (s != factory.goingToJobSite()) {
                return s;
            } else if (inventory.inventoryFull() || (hasItems && !town.hasSupplies())) {
                return s;
            }
        }

        STATUS s2 = s;
        if (s2 != null) {
            return nullIfUnchanged(currentStatus, s2);
        } else if (inventory.hasNonSupplyItems(town.isCachingAllowed())) {
            if (town.hasSpace()) {
                s2 = factory.droppingLoot();
            } else {
                s2 = factory.noSpace();
            }
        } else if (!town.hasSupplies()) {
            if (town.canUseMoreSupplies()) {
                s2 = nullIfUnchanged(currentStatus, factory.noSupplies());
            } else if (hasItems) {
                s2 = nullIfUnchanged(currentStatus, factory.droppingLoot());
            } else {
                s2 = factory.noJobSite();
            }
        } else {
            if (hasItems && !hasWorkItems) {
                s2 = nullIfUnchanged(currentStatus, factory.droppingLoot());
            } else if (town.canUseMoreSupplies()) {
                s2 = nullIfUnchanged(currentStatus, factory.collectingSupplies());
            } else {
                if (town.isTimerActive()) {
                    return factory.waitingForTimedState();
                }
                return factory.noJobSite();
            }
        }

        if (s2 != factory.collectingSupplies() && s != null) {
            return s;
        }

        return s2;
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
    public static <STATUS extends IProductionStatus<STATUS>, ROOM extends Room> STATUS productionRoutine(
            STATUS currentStatus,
            boolean prioritizeExtraction,
            EntityInvStateProvider<STATUS> inventory,
            EntityLocStateProvider<ROOM> entity,
            JobTownProvider<ROOM, STATUS> town,
            IProductionJob<STATUS> job,
            IProductionStatusFactory<STATUS> factory
    ) {
        if (factory.waitingForTimedState()
                   .equals(currentStatus)) {
            if (town.isUnfinishedTimeWorkPresent()) {
                return null;
            }
        }
        STATUS status = usualRoutineV2(
                currentStatus, prioritizeExtraction, inventory,
                new TownStateProvider() {
                    @Override
                    public boolean hasSupplies() {
                        return town.hasSupplies();
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
                },
                new Job<STATUS, STATUS>() {
                    @Override
                    public @Nullable STATUS tryChoosingItemlessWork() {
                        Collection<Integer> states = town.getStatesWithUnfinishedItemlessWork();
                        for (Integer state : states) {
                            return factory.fromJobBlockState(state);
                        }

                        Collection<ROOM> rooms = town.roomsWithCompletedProduct();
                        if (rooms.isEmpty()) {
                            return null;
                        }

                        ROOM location = entity.getEntityCurrentJobSite();
                        if (location != null) {
                            if (rooms.contains(location)) {
                                return factory.extractingProduct();
                            }
                        }

                        return factory.goingToJobSite();
                    }

                    @Override
                    public ImmutableMap<Integer, Supplier<@Nullable STATUS>> getItemlessWorkKeyedByPriority() {
                        Collection<Integer> workReadyToDo = town.getStatesWithUnfinishedItemlessWork();
                        ImmutableList<STATUS> allByPref = job.getAllWorkStatesSortedByPreference();
                        Map<Integer, Supplier<STATUS>> b = new HashMap<>();
                        for (int i = 0; i < allByPref.size(); i++) {
                            STATUS potentialStatus = allByPref.get(i);
                            if (workReadyToDo.contains(potentialStatus.value())) {
                                b.put(i, () -> potentialStatus);
                                break;
                            }
                        }

                        b.compute(job.getMaxState().value(), (idx, cur) -> () -> {
                            if (cur != null) {
                                return JobsClean.doOrGoTo(
                                        cur.get(),
                                        entity.getEntityCurrentJobSite() != null,
                                        factory.goingToJobSite()
                                );
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
                        });

                        return ImmutableMap.copyOf(b);
                    }

                    @Override
                    public @Nullable STATUS tryUsingSupplies(Map<STATUS, Boolean> supplyItemStatus) {
                        if (supplyItemStatus.isEmpty()) {
                            return null;
                        }
                        ROOM location = entity.getEntityCurrentJobSite();
                        Map<STATUS, ? extends Collection<ROOM>> roomNeedsMap = town.roomsToGetSuppliesForByState();

                        boolean foundWork = false;

                        List<STATUS> orderedWithSupplies = job.getAllWorkStatesSortedByPreference()
                                                               .stream()
                                                               .filter(work -> supplyItemStatus.getOrDefault(
                                                                       work, false))
                                                               .toList();

                        for (STATUS s : orderedWithSupplies) {
                            if (roomNeedsMap.containsKey(s) && !roomNeedsMap.get(s)
                                                                            .isEmpty()) { // TODO: Unit test the second leg of this condition
                                foundWork = true;
                                if (location != null) {
                                    if (roomNeedsMap.get(s)
                                                    .contains(location)) {
                                        return s;
                                    }
                                }
                            }
                        }

                        if (foundWork) {
                            return factory.goingToJobSite();
                        }
                        // TODO: Return null here. This call to `try` might be needed for the farmer job.
                        //  Let's convert that into a production job.
                        return job.tryUsingSupplies(supplyItemStatus);
                    }

                    @Override
                    public ImmutableMap<Integer, Supplier<@Nullable STATUS>> getSupplyUsesKeyedByPriority(Map<STATUS, Boolean> supplyItemStatus) {
                        if (supplyItemStatus.isEmpty()) {
                            return null;
                        }
                        ROOM location = entity.getEntityCurrentJobSite();
                        final Map<STATUS, ? extends Collection<ROOM>> roomNeedsMap = town.roomsToGetSuppliesForByState();

                        ImmutableList<STATUS> allByPref = job.getAllWorkStatesSortedByPreference();
                        ImmutableMap.Builder<Integer, Supplier<STATUS>> b = ImmutableMap.builder();
                        for (int i = 0; i < allByPref.size(); i++) {
                            final STATUS s = allByPref.get(i);
                            boolean hasSuppliesForStatus = Util.getOrDefault(supplyItemStatus, s, false);
                            if (hasSuppliesForStatus) {
                                b.put(i, () -> {
                                    if (roomNeedsMap.containsKey(s) && !roomNeedsMap.get(s)
                                                                                    .isEmpty()) { // TODO: Unit test the second leg of this condition
                                        if (location != null) {
                                            if (roomNeedsMap.get(s)
                                                            .contains(location)) {
                                                return s;
                                            }
                                        }
                                        return factory.goingToJobSite();
                                    }
                                    return null;
                                });
                            }
                        }
                        return b.build();
                    }
                }, factory
        );
        // TODO: For "no supplies" status, ignore rooms that only need tools
        // Because rooms needing tools "need supplies" at all times, the logic chooses that status.
        if (status == null || factory.idle()
                                     .equals(status) || factory.noSupplies()
                                                               .equals(status)) {
            if (town.isUnfinishedTimeWorkPresent()) {
                return factory.waitingForTimedState();
            }
        }
        return status;
    }

    private static <S> S nullIfUnchanged(
            S oldStatus,
            S newStatus
    ) {
        if (oldStatus == newStatus) {
            return null;
        }
        return newStatus;
    }
}

