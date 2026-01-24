package ca.bradj.questown.jobs;

import ca.bradj.questown.QT;
import ca.bradj.questown.core.Pair;
import ca.bradj.questown.jobs.declarative.WithReason;
import ca.bradj.questown.jobs.production.IProductionJob;
import ca.bradj.questown.jobs.production.IProductionStatus;
import ca.bradj.questown.jobs.production.RoomsNeedingVillagerInput;
import ca.bradj.roomrecipes.core.Room;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Stream;

public class JobStatuses {

    public static boolean hasItems(EntityInvStateProvider<?> inventory) {
        if (inventory.hasNonSupplyItems()) {
            return true;
        }
        return inventory.getSupplyItemStatus().values().stream().anyMatch(v -> v == SupplyItemStatus.HAS_ITEM);
    }

    public interface Job<STATUS, SUP_CAT> {
        @Nullable
        STATUS tryChoosingItemlessWork();

        @Nullable
        STATUS tryUsingSupplies(Map<SUP_CAT, SupplyItemStatus> supplyItemStatus);
    }

    /**
     * @deprecated Use version with context object
     */
    @Deprecated(since = "0.0.9")
    public static <STATUS extends IStatus<STATUS>, SUP_CAT> STATUS usualRoutine(
            STATUS currentStatus,
            boolean prioritizeExtraction,
            EntityInvStateProvider<SUP_CAT> inventory,
            TownStateProvider town,
            Job<STATUS, SUP_CAT> job,
            IStatusFactory<STATUS> factory
    ) {
        return usualRoutine(new UsualRoutineContext<>(
                new JobID("unknown", "unknown"),
                currentStatus,
                prioritizeExtraction,
                inventory,
                town, job, factory
        ));
    }

    public record UsualRoutineContext<STATUS extends IStatus<STATUS>, SUP_CAT>(
            JobID jobId,
            STATUS currentStatus,
            boolean prioritizeExtraction,
            EntityInvStateProvider<SUP_CAT> inventory,
            TownStateProvider town,
            Job<STATUS, SUP_CAT> job,
            IStatusFactory<STATUS> factory
    ) {}

    public static <STATUS extends IStatus<STATUS>, SUP_CAT> STATUS usualRoutine(
            UsualRoutineContext<STATUS, SUP_CAT> ctx
    ) {
        LZCD<STATUS> root = usualRoutineRoot(ctx);
        root.initializeAll();
        return nullIfUnchanged(ctx.currentStatus, root.resolve());
    }

    public static <STATUS extends IStatus<STATUS>, SUP_CAT> @NotNull LZCD<STATUS> usualRoutineRoot(
            UsualRoutineContext<STATUS, SUP_CAT> ctx
    ) {
        EntityInvStateProvider<SUP_CAT> inventory = ctx.inventory();
        Map<SUP_CAT, SupplyItemStatus> supplyItemStatus = inventory.getSupplyItemStatus();

        LZCD<LZCD.Dependency<STATUS>> dHasWorkItems = prePopAble(
                new Pair<>(ctx.jobId, "hasWorkItems"),
                () -> supplyItemStatus.containsValue(SupplyItemStatus.HAS_ITEM)
        );
        LZCD<LZCD.Dependency<STATUS>> dHasNonWorkItems = prePopAble(
                new Pair<>(ctx.jobId, "hasNonWorkItems"),
                inventory::hasNonSupplyItems
        );
        LZCD<LZCD.Dependency<STATUS>> dHasAnyItems = prePopAble(
                new Pair<>(ctx.jobId, "hasAnyItems"),
                () -> supplyItemStatus.containsValue(SupplyItemStatus.HAS_ITEM) || inventory.hasNonSupplyItems()
        );
        LZCD<LZCD.Dependency<STATUS>> dInventoryEmpty = prePopAble(
                new Pair<>(ctx.jobId, "inventory empty"),
                () -> !supplyItemStatus.containsValue(SupplyItemStatus.HAS_ITEM) || !inventory.hasNonSupplyItems()
        );
        LZCD<LZCD.Dependency<STATUS>> dInventoryFull = prePopAble(
                new Pair<>(ctx.jobId, "inventory full"),
                inventory::inventoryFull
        );
        ILZCD<LZCD.Dependency<STATUS>> dPrioritizeExtraction = prePopAble(
                new Pair<>(ctx.jobId, "prioritizing extraction"),
                () -> ctx.prioritizeExtraction
        );
        ILZCD<LZCD.Dependency<STATUS>> dStatusNotGoing = input(
                new Pair<>(ctx.jobId, "not going to jobsite"),
                s -> !ctx.factory.goingToJobSite().equals(s)
        );
        TownStateProvider town = ctx.town;
        ILZCD<LZCD.Dependency<STATUS>> dTownHasSpace = fromVoid(town.hasSpace());
        ILZCD<LZCD.Dependency<STATUS>> dTimerActive = fromVoid(town.isTimerActive());
        ILZCD<LZCD.Dependency<STATUS>> dTownHasSupplies = fromVoid(town.hasSupplies());
        ILZCD<LZCD.Dependency<STATUS>> dTownHasNoSupplies = fromVoid(LZCDs.invert(town.hasSupplies()));
        ILZCD<LZCD.Dependency<STATUS>> dHasWorkableBlocks = fromVoid(town.containsWorkableBlocksAtAnyState());
        ILZCD<LZCD.Dependency<STATUS>> dHasNoWorkableBlocks = fromVoid(LZCDs.invert(town.containsWorkableBlocksAtAnyState()));
        Job<STATUS, SUP_CAT> job = ctx.job;
        IStatusFactory<STATUS> factory = ctx.factory;
        LZCD<STATUS> root = new LZCD<>(
                new Pair<>(ctx.jobId, "work without items"),
                LZCDs.leaf(job::tryChoosingItemlessWork, Objects::isNull),
                ImmutableList.of(
                        dPrioritizeExtraction,
                        dStatusNotGoing
                ),
                new LZCD<>(
                        new Pair<>(ctx.jobId, "use items"),
                        LZCDs.leaf(() -> job.tryUsingSupplies(supplyItemStatus), Objects::isNull),
                        ImmutableList.of(
                                dHasWorkItems,
                                dHasWorkableBlocks
                        ),
                        new LZCD<>(
                                new Pair<>(ctx.jobId, "work in different room without items"),
                                LZCDs.leaf(job::tryChoosingItemlessWork, Objects::isNull),
                                ImmutableList.of(
                                        dPrioritizeExtraction,
                                        dHasWorkableBlocks
                                ),
                                new LZCD<>(
                                        new Pair<>(ctx.jobId, "drop loot when hands full"),
                                        leaf(factory::droppingLoot),
                                        ImmutableList.of(
                                                dInventoryFull,
                                                dTownHasSpace
                                        ),
                                        LZCDs.oneDep(
                                                new Pair<>(ctx.jobId, "stop when no space and hands full"),
                                                leaf(factory::noSpace),
                                                dInventoryFull,
                                                new LZCD<>(
                                                        new Pair<>(ctx.jobId, "drop loot from non-full hands before starting more work"),
                                                        leaf(factory::droppingLoot),
                                                        ImmutableList.of(
                                                                dHasNonWorkItems,
                                                                dTownHasSpace
                                                        ),
                                                        new LZCD<>(
                                                                new Pair<>(ctx.jobId, "get work supplies"),
                                                                leaf(factory::collectingSupplies),
                                                                ImmutableList.of(
                                                                        dTownHasSupplies
                                                                        // This used to include a check to ensure there
                                                                        // is a jobsite. But many jobs are unable to
                                                                        // identify a jobsite until the villager is
                                                                        // holding the ingredient. (e.g. arborist/sapling)
                                                                ),
                                                                new LZCD<>(
                                                                        new Pair<>(ctx.jobId, "drop loot when no work supplies available"),
                                                                        leaf(factory::droppingLoot),
                                                                        ImmutableList.of(
                                                                                dHasNonWorkItems,
                                                                                dHasWorkableBlocks,
                                                                                dTownHasSpace
                                                                        ),
                                                                        new LZCD<>(
                                                                                new Pair<>(ctx.jobId, "drop loot when no work possible"),
                                                                                leaf(factory::droppingLoot),
                                                                                ImmutableList.of(
                                                                                        dHasAnyItems,
                                                                                        dTownHasSpace
                                                                                ),
                                                                                new LZCD<>(
                                                                                        new Pair<>(ctx.jobId, "wait for next stage is timer is active"),
                                                                                        leaf(factory::waitingForTimedState),
                                                                                        ImmutableList.of(
                                                                                                dTimerActive
                                                                                        ),
                                                                                        new LZCD<>(
                                                                                                new Pair<>(ctx.jobId, "extract results"),
                                                                                                LZCDs.leaf(job::tryChoosingItemlessWork, Objects::isNull),
                                                                                                ImmutableList.of(
                                                                                                        dStatusNotGoing
                                                                                                ),
                                                                                                new LZCD<>(
                                                                                                        new Pair<>(ctx.jobId, "stop (nojobsite) when nowhere to work and town has items"),
                                                                                                        leaf(factory::noJobSite),
                                                                                                        ImmutableList.of(
                                                                                                                dTownHasSupplies,
                                                                                                                dInventoryEmpty
                                                                                                        ),
                                                                                                        new LZCD<>(
                                                                                                                new Pair<>(ctx.jobId, "stop when no space and holding any items"),
                                                                                                                leaf(factory::noSpace),
                                                                                                                ImmutableList.of(
                                                                                                                        dHasAnyItems
                                                                                                                ),

                                                                                                                new LZCD<>(
                                                                                                                        new Pair<>(ctx.jobId, "stop when no jobsite and no usable supplies in town"),
                                                                                                                        leaf(factory::noJobSite),
                                                                                                                        ImmutableList.of(
                                                                                                                                dInventoryEmpty,
                                                                                                                                dTownHasNoSupplies,
                                                                                                                                dHasNoWorkableBlocks
                                                                                                                        ),
                                                                                                                        leaf(factory::noSupplies)
                                                                                                                )
                                                                                                        )
                                                                                                )
                                                                                        )
                                                                                )
                                                                        )
                                                                )
                                                        )
                                                )
                                        )
                                )
                        )
                )
        );
        return root;
    }

    private static <STATUS extends IStatus<STATUS>> ILZCD<LZCD.Dependency<STATUS>> fromVoid(
            LZCD.Dependency<Void> dep
    ) {
        //noinspection unchecked,rawtypes
        return LZCDs.noDeps(
                new Pair(new JobID("unknown", "unknown"), dep.getName()),
                () -> (LZCD.Dependency) dep,
                Objects::isNull
        );
    }

    private static <STATUS extends IStatus<STATUS>> @NotNull ILZCD<STATUS> leaf(Supplier<STATUS> factory) {
        return LZCDs.leaf(factory, Objects::isNull);
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
            EntityInvStateProvider<Integer> inventory,
            EntityLocStateProvider<ROOM> entity,
            JobTownProvider<ROOM> town,
            IProductionJob<STATUS> job,
            IProductionStatusFactory<STATUS> factory
    ) {
        if (factory.waitingForTimedState().equals(currentStatus)) {
            if (town.isUnfinishedTimeWorkPresent()) {
                return null;
            }
        }
        STATUS status = usualRoutine(
                currentStatus, prioritizeExtraction, inventory,
                JobTownStates.forTown(town),
                new Job<>() {
                    @Override
                    public @Nullable STATUS tryChoosingItemlessWork() {
                        ROOM location = entity.getEntityCurrentJobSite();
                        Collection<Integer> states = town.getStatesWithUnfinishedItemlessWork();
                        if (!states.isEmpty()) {
                            for (Integer state : states) {
                                // TODO: Unit test
                                if (location != null) {
                                    if (town.roomsAtState(state).contains(location)) {
                                        return factory.fromJobBlockState(state);
                                    }
                                }
                            }
                            return factory.goingToJobSite();
                        }

                        Collection<ROOM> rooms = town.roomsWithCompletedProduct();
                        if (rooms.isEmpty()) {
                            return null;
                        }

                        if (location != null) {
                            if (rooms.contains(location)) {
                                return factory.extractingProduct();
                            }
                        }

                        return factory.goingToJobSite();
                    }

                    @Override
                    public @Nullable STATUS tryUsingSupplies(Map<Integer, SupplyItemStatus> supplyItemStatus) {
                        if (supplyItemStatus.isEmpty()) {
                            return null;
                        }
                        ROOM location = entity.getEntityCurrentJobSite();
                        RoomsNeedingVillagerInput<ROOM, ?, ?> roomNeedsMap = town.roomsNeedingIngredientsByState()
                                                                                 .floor();

                        boolean foundWork = false;

                        List<Integer> orderedWithSupplies = job.getAllWorkStatesSortedByPreference()
                                                               .stream()
                                                               .filter(work -> supplyItemStatus.getOrDefault(
                                                                       work,
                                                                       SupplyItemStatus.NOT_REQUIRED
                                                               ) != SupplyItemStatus.NEEDS_ITEM)
                                                               .toList();

                        for (Integer s : orderedWithSupplies) {
                            if (roomNeedsMap.containsKey(s) && !roomNeedsMap.get(s)
                                                                            .isEmpty()) { // TODO: Unit test the second leg of this condition
                                foundWork = true;
                                if (location != null) {
                                    Stream<? extends RoomsNeedingVillagerInput.NVIRoom<ROOM, ?, ?>> stream = roomNeedsMap.get(
                                            s).stream();

                                    // TODO: Assess whether this is needed
                                    stream = stream.filter(v -> !v.dueToWorkOnly());

                                    if (stream.anyMatch(v -> location.equals(v.room().getRoom()))) {
                                        return factory.fromJobBlockState(s);
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
                }, factory
        );
        // TODO: For "no supplies" status, ignore rooms that only need tools
        // Because rooms needing tools "need supplies" at all times, the logic chooses that status.
        if (status == null || factory.idle().equals(status) || factory.noSupplies().equals(status)) {
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

    private static <STATUS> LZCD<LZCD.Dependency<STATUS>> prePopAble(
            Pair<JobID,String> name,
            Supplier<Boolean> s
    ) {
        return LZCDs.noDeps(
                name,
                () -> new LZCD.Dependency<STATUS>() {
                    private WithReason<Boolean> value;

                    @Override
                    public Populated<WithReason<Boolean>> populate() {
                        // TODO: Pass dependencies as inputs to usualRoutine
                        this.value = WithReason.always(s.get(), "input");
                        return new Populated<>(
                                name.toString(),
                                value,
                                ImmutableMap.of(),
                                null
                        ) {
                            @Override
                            protected String stringRep() {
                                return "PrePopulatable[" + value + "]";
                            }
                        };
                    }

                    @Override
                    public String describe() {
                        String v = value == null ? "<?>" : value.toString();
                        return name.toString() + '=' + v;
                    }

                    @Override
                    public String getName() {
                        return name.b();
                    }

                    @Override
                    public WithReason<Boolean> apply(Supplier<STATUS> statusSupplier) {
                        populate();
                        return this.value;
                    }
                }, v -> false
        );
    }

    private static <STATUS> LZCD<LZCD.Dependency<STATUS>> input(
            Pair<JobID, String> name,
            Function<STATUS, Boolean> s
    ) {
        return LZCDs.noDeps(
                name,
                () -> new LZCD.Dependency<STATUS>() {
                    @Override
                    public Populated<WithReason<Boolean>> populate() {
                        // Cannot be pre-populated
                        return new Populated<>(
                                name.b(),
                                WithReason.always(null, "cannot be pre-computed"),
                                ImmutableMap.of(),
                                null
                        ) {
                            @Override
                            protected String stringRep() {
                                return "Input[Value TBD]";
                            }
                        };
                        // TODO: Pass dependencies as inputs to usualRoutine
                    }

                    @Override
                    public String describe() {
                        return name + "=<?>";
                    }

                    @Override
                    public String getName() {
                        return name.b();
                    }

                    @Override
                    public WithReason<Boolean> apply(Supplier<STATUS> statusSupplier) {
                        return WithReason.always(s.apply(statusSupplier.get()), "input");
                    }
                }, v -> false
        );
    }
}
