package ca.bradj.questown.town.entity;

import ca.bradj.questown.QT;
import ca.bradj.questown.Questown;
import ca.bradj.questown.commands.DebugLogArgument;
import ca.bradj.questown.core.Config;
import ca.bradj.questown.core.UtilClean;
import ca.bradj.questown.core.VillagerUUID;
import ca.bradj.questown.core.advancements.TutorialTrigger;
import ca.bradj.questown.core.init.AdvancementsInit;
import ca.bradj.questown.integration.minecraft.*;
import ca.bradj.questown.jobs.*;
import ca.bradj.questown.jobs.declarative.DowntimeWork;
import ca.bradj.questown.jobs.declarative.WarpTickHook;
import ca.bradj.questown.jobs.leaver.ContainerTarget;
import ca.bradj.questown.jobs.requests.WorkRequest;
import ca.bradj.questown.mc.Compat;
import ca.bradj.questown.mobs.visitor.VisitorMobEntity;
import ca.bradj.questown.town.TownContainers;
import ca.bradj.questown.town.TownState;
import ca.bradj.questown.town.TownVillagerData;
import ca.bradj.questown.town.interfaces.TownInterface;
import ca.bradj.questown.integration.SpecialRulesRegistry;
import ca.bradj.questown.integration.jobs.JobPhaseModifier;
import ca.bradj.questown.world.MinecraftWorldAccess;
import ca.bradj.questown.world.WarpWorldAccess;
import ca.bradj.roomrecipes.adapter.Positions;
import ca.bradj.roomrecipes.logic.InclusiveSpaces;
import ca.bradj.roomrecipes.serialization.MCRoom;
import com.google.common.base.Function;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.items.IItemHandler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.function.Predicate;
import java.util.function.Supplier;

// This class is NOT encapsulated from MC

public class TownFlagState {

    /**
     * Interface for work-related operations during warp.
     * This bridges to the job registry and town state.
     */
    public interface Work {
        void recomputeNow();

        @Nullable
        ca.bradj.questown.jobs.JobID getRandomFinishableWork(
                ca.bradj.questown.jobs.JobID jobID,
                ca.bradj.questown.jobs.Signals.DayTime dayTime,
                long ticksElapsed
        );

        java.util.List<ca.bradj.questown.jobs.JobID> getPreselectedJobs(
                ca.bradj.questown.jobs.JobID currentJob
        );

        long getTotalDuration(
                ca.bradj.questown.jobs.JobID jobID,
                ca.bradj.questown.core.VillagerUUID vuid
        );

        int getWarpTicksPerCycle(
                ca.bradj.questown.jobs.JobID jobID,
                ca.bradj.questown.core.VillagerUUID vuid
        );
    }
    static final String NBT_TIME_WARP_REFERENCE_TICK = String.format("%s_last_tick", Questown.MODID);
    static final String NBT_TOWN_STATE = String.format("%s_town_state", Questown.MODID);
    private final TownFlagBlockEntity parent;
    private boolean initialized = false;
    private final Stack<Function<TownFlagBlockEntity, MCTownState>> townInit = new Stack<>();

    private final Map<BlockPos, Integer> listenedBlocks = new HashMap<>();

    private long ticksSinceContainerScan = 0;
    private final ArrayList<Integer> times = new ArrayList<>();

    public TownFlagState(TownFlagBlockEntity parent) {
        this.parent = parent;
    }

    @Nullable MCTownState captureState() {
        ImmutableList.Builder<TownState.VillagerData<MCHeldItem>> vB = ImmutableList.builder();
        for (LivingEntity entity : parent.getVillagerHandle().entities()) {
            if (entity instanceof VisitorMobEntity) {
                if (!((VisitorMobEntity) entity).isInitialized()) {
                    return null;
                }
                Vec3 pos = entity.position();
                ImmutableSnapshot<MCHeldItem, ?> snapshot = ((VisitorMobEntity) entity).getJobJournalSnapshot();
                TownState.VillagerData<MCHeldItem> data = new TownState.VillagerData<MCHeldItem>(
                        pos.x, pos.y, pos.z, snapshot, entity.getUUID(),
                        parent.getVillagerHandle().getProficiencies(entity.getUUID())
                );
                vB.add(data);
            }
        }

        long dayTime = parent.getServerLevel().getDayTime();
        return new MCTownState(
                vB.build(),
                TownContainers.findAllContainersMatching(parent, item -> true).toList(),
                // TODO[Warp]: Store statuses for all villagers
                parent.getWorkStatusHandle(null).getAll(),
                ImmutableMap.of(), // TODO: Store timers from world
                parent.getWelcomeMats(),
                ImmutableList.of(), // TODO: Should we pass in current knowledge?
                UtilClean.mapKeys(parent.villagerHandle.blockOfProgressMap(), VillagerUUID::get),
                dayTime
        );
    }

    static MCTownState advanceTime(
            TownFlagBlockEntity e,
            ServerLevel sl,
            @Nullable Long optionalWarpDuration
    ) {
        long dayTime = sl.getDayTime();
        if (e.advancedTimeOnTick == dayTime) { // TODO[Warp]: Plus or minus some ticks?
            e.getDebugLogger(QT.FLAG_LOGGER, DebugLogArgument.TIME_WARP).log("Already advanced time on this tick. Skipping.");
            return null;
        }

        e.advancedTimeOnTick = dayTime;

        MCTownState storedState;
        if (Compat.getBlockStoredTagData(e).contains(NBT_TOWN_STATE)) {
            storedState = TownStateSerializer.INSTANCE.load(
                    Compat.getBlockStoredTagData(e).getCompound(NBT_TOWN_STATE),
                    sl, bp -> e.getWelcomeMats().contains(bp)
            );
            e.getDebugLogger(QT.FLAG_LOGGER, DebugLogArgument.TIME_WARP).log("Loaded state from NBT: {}", storedState);
        } else {
            storedState = new MCTownState(
                    ImmutableList.of(),
                    ImmutableList.of(),
                    ImmutableMap.of(),
                    ImmutableMap.of(),
                    ImmutableList.of(),
                    ImmutableList.of(),
                    ImmutableMap.of(),
                    0
            );
            QT.logBug("NBT had no town state. That's probably a bug. Town state will reset");
        }

        long ticksPassed = dayTime - storedState.worldTimeAtSleep;
        if (optionalWarpDuration != null) {
            ticksPassed = optionalWarpDuration;
        }
        if (ticksPassed <= 0) {
            QT.FLAG_LOGGER.info("Time warp is not applicable");
            return storedState;
        }

        // Two timelines (ADR-0006): villager labour runs on the productive budget (stops at
        // night); passive world processes (crop/furnace/tree warp rules) run on the wall-clock budget.
        long wallClockTicks = Math.min(ticksPassed, Config.TIME_WARP_MAX_TICKS.get());
        long productiveTicks = Signals.calculateProductiveTicks(dayTime, wallClockTicks);
        Signals.ProductiveWallClockTimeline timeline =
                Signals.buildProductiveWallClockTimeline(dayTime, wallClockTicks);
        if (productiveTicks <= 0) {
            QT.FLAG_LOGGER.info(
                    "Time warp window is all night ({} wall-clock ticks): running passive effects only",
                    wallClockTicks
            );
        }

        // Create Work implementation that bridges to entity methods
        Work w = createWork(e, sl);

        // Collect real room block positions for warp rules
        ImmutableList.Builder<BlockPos> roomPosBuilder = ImmutableList.builder();
        for (MCRoom room : e.roomsHandle.getAllRoomsIncludingMetaAndFarms()) {
            room.getSpaces().stream()
                    .flatMap(space -> InclusiveSpaces.getPositions(space, InclusiveSpaces.PositionType.INTERIOR_ONLY).stream())
                    .forEach(v -> {
                        BlockPos pos = Positions.ToBlock(v, room.yCoord);
                        roomPosBuilder.add(pos);
                        roomPosBuilder.add(pos.above());
                    });
        }

        // TODO: Consider pre-allocating roomPositions to jobIds based on JobBlock match and passing them in to advance time
        ImmutableList<BlockPos> roomPositions = roomPosBuilder.build();

        ImmutableSet<String> rules = collectGlobalRulesForAllVillagerRoots(storedState);

        WarpWorldAccess warpWorld = new WarpWorldAccess(sl, roomPositions);
        warpWorld.seedFarmSaplings(collectFarmPositions(e));

        MCAdvanceTime.WarpTickCallback<MCTownState> warpCb =
                (town, tick, delta) -> {
                    // Plantings during the upcoming villager steps record this tick as their plantTick.
                    warpWorld.setCurrentWarpTick(tick);
                    return WarpTickHook.run(
                            rules,
                            warpWorld,
                            town, tick, delta,
                            () -> roomPositions
                    );
                };

        // Delegate to MCAdvanceTime (the testable implementation)
        MCAdvanceTime advancer =
                new MCAdvanceTime(Config.MAX_DOWNTIME_TICKS.get());
        MCAdvanceTime.Result<MCTownState> result = advancer.advanceTime(
                storedState,
                productiveTicks,
                wallClockTicks,
                dayTime,
                timeline::wallClockOffsetAt,
                ImportantTicks.adaptWork(w),
                MCAdvanceTime.createWarperFactory(w, e.getBlockPos(), roomPositions, warpWorld),
                null, // cookResolver - not yet implemented
                warpCb,
                sl,
                job -> DowntimeWork.matches(job),
                Config.MAX_DOWNTIME_TICKS.get(),
                createLogger(e)
        );

        if (result.state() != null) {
            warpWorld.applyTo(sl);
        }

        return result.state();
    }

    private static ImmutableList<BlockPos> collectFarmPositions(TownFlagBlockEntity e) {
        ImmutableList.Builder<BlockPos> farmPosBuilder = ImmutableList.builder();
        for (MCRoom room : e.roomsHandle.getFarms()) {
            room.getSpaces().stream()
                    .flatMap(space -> InclusiveSpaces.getPositions(space, InclusiveSpaces.PositionType.INTERIOR_ONLY).stream())
                    .forEach(v -> {
                        BlockPos pos = Positions.ToBlock(v, room.yCoord);
                        farmPosBuilder.add(pos);
                        farmPosBuilder.add(pos.above());
                    });
        }
        return farmPosBuilder.build();
    }

    private static ImmutableSet<String> collectGlobalRulesForAllVillagerRoots(
            MCTownState storedState
    ) {
        ImmutableSet<String> activeRoots = storedState.villagers.stream()
                .map(v -> v.journal.jobId().rootId())
                .collect(ImmutableSet.toImmutableSet());
        ImmutableSet.Builder<String> ruleIDs = ImmutableSet.builder();
        for (JobID id : Works.ids()) {
            if (!activeRoots.contains(id.rootId())) {
                continue;
            }
            Supplier<ca.bradj.questown.jobs.Work> ws = Works.get(id);
            if (ws != null) {
                ruleIDs.addAll(ws.get().getSpecialGlobalRules());
            }
        }
        return ruleIDs.build();
    }

    /**
     * Creates the Work implementation that bridges to the TownFlagBlockEntity.
     */
    private static Work createWork(TownFlagBlockEntity e, ServerLevel sl) {
        return new Work() {
            private final TownVillagerData.FallbackSelector fallbackSelector = new TownVillagerData.FallbackSelector();

            @Override
            public void recomputeNow() {
                e.getPossibleWork().recomputeNow();
            }

            @Override
            public @Nullable JobID getRandomFinishableWork(
                    JobID currentJob,
                    Signals.DayTime dayTime,
                    long ticksElapsed
            ) {
                ImmutableList<WorkRequest> requestedResults = e.getWorkHandle().getRequestedResults();
                WorksBehaviour.TownData td = e.getTownData();

                Predicate<JobID> canFit = p -> ServerJobsRegistry.canFit(null, p, dayTime);
                Predicate<JobID> canAlwaysStart = p -> ServerJobsRegistry.canAlwaysStart(null, p);

                List<JobID> possibleWork = e.getPossibleWork().getFor(currentJob);

                // First try to choose from preselected jobs that match a request
                JobID work = TownVillagerData.chooseFromList(
                        canFit,
                        canAlwaysStart,
                        requestedResults,
                        td,
                        possibleWork
                );
                if (work != null) {
                    return work;
                }

                JobID fallback = fallbackSelector.tryFallback(
                        (int) ticksElapsed, currentJob, canFit, canAlwaysStart, requestedResults, td,
                        e.getPossibleWork().getFor(currentJob),
                        jobs -> Compat.shuffle(jobs.iterator(), sl)
                );
                if (fallback != null) {
                    return fallback;
                }
                if (fallbackSelector.isBuffering()) {
                    return currentJob;
                }
                return null;
            }

            @Override
            public List<JobID> getPreselectedJobs(JobID currentJob) {
                return e.getPossibleWork().getFor(currentJob);
            }

            @Override
            public long getTotalDuration(JobID jobID, VillagerUUID vuid) {
                return ServerJobsRegistry.getUninitializedJob(jobID, vuid).getJob().getTotalDuration();
            }

            @Override
            public int getWarpTicksPerCycle(JobID jobID, VillagerUUID vuid) {
                return ServerJobsRegistry.getUninitializedJob(jobID, vuid).getJob().getWarpTicksPerCycle();
            }
        };
    }

    /**
     * Creates a WarpLogger that respects the debug logging configuration.
     */
    private static MCAdvanceTime.WarpLogger createLogger(TownFlagBlockEntity e) {
        return new MCAdvanceTime.WarpLogger() {
            @Override
            public void log(String message, Object... args) {
                TownInterface.DebugLogger logger = Config.LOG_WARP_RESULT.get() ?
                        QT.FLAG_LOGGER::info :
                        e.getDebugLogger(QT.FLAG_LOGGER, DebugLogArgument.TIME_WARP);
                logger.log(message, args);
            }

            @Override
            public void logDetail(String message, Object... args) {
                // Use TIME_WARP for detail logs as well
                e.getDebugLogger(QT.FLAG_LOGGER, DebugLogArgument.TIME_WARP).log(message, args);
            }
        };
    }

    static void recoverMobs(
            TownFlagBlockEntity e,
            ServerLevel sl
    ) {
        ImmutableList<LivingEntity> entitiesSnapshot = ImmutableList.copyOf(e.getVillagerHandle().entities());
        for (LivingEntity entity : entitiesSnapshot) {
            e.getVillagerHandle().remove(entity);
            entity.stopSleeping();
            entity.remove(Entity.RemovalReason.DISCARDED);
        }

        if (Compat.getBlockStoredTagData(e).contains(NBT_TOWN_STATE)) {
            @NotNull ImmutableList<TownState.VillagerData<MCHeldItem>> villagers = TownStateSerializer.loadVillagers(
                    Compat.getBlockStoredTagData(e).getCompound(NBT_TOWN_STATE)
            );
            for (TownState.VillagerData<MCHeldItem> v : villagers) {
                VisitorMobEntity recovered = new VisitorMobEntity(sl, e);
                recovered.initialize(
                        e,
                        v.uuid,
                        v.xPosition,
                        v.yPosition,
                        v.zPosition,
                        v.journal
                );
                sl.addFreshEntity(recovered);
                e.getVillagerHandle().register(recovered);
                // Restore persisted proficiencies over any seed the register() call may have applied
                // (loaded/relocated townies keep theirs — ADR-0010).
                if (!v.getProficiencies().isEmpty()) {
                    e.getVillagerHandle().setProficiencies(v.uuid, v.getProficiencies());
                }
            }
            e.getDebugLogger(QT.FLAG_LOGGER, DebugLogArgument.TIME_WARP).log("Loaded villager state from NBT: {}", villagers);
        }
    }

    public void load(CompoundTag tag) {
        if (tag.contains(NBT_TOWN_STATE)) {
            CompoundTag stateTag = tag.getCompound(NBT_TOWN_STATE);
            this.townInit.push((e) -> TownStateSerializer.INSTANCE.load(
                    stateTag, e.getServerLevel(), bp -> e.getWelcomeMats().contains(bp)
            ));
        }
    }

    // Returns true if changes detected
    public boolean tick(
            TownFlagBlockEntity e,
            CompoundTag flagTag,
            ServerLevel level
    ) {
        if (!e.isInitialized()) {
            return false;
        }

        long start = System.currentTimeMillis();
        long lastTick = flagTag.getLong(NBT_TIME_WARP_REFERENCE_TICK);
        long gt = level.getDayTime();
        long timeSinceWake = Math.max(
                0,
                gt - lastTick
        );
        boolean waking = timeSinceWake > 10 || !initialized;
        this.initialized = true;

        if (waking) {
            warp(e, flagTag, level, timeSinceWake);
        } else {
            flagTag.putLong(NBT_TIME_WARP_REFERENCE_TICK, gt);
        }

        if (!isDueForContainerScan()) {
            profileTick(start);
            return false;
        }

        Iterator<ContainerTarget<MCContainer, MCTownItem>> matchIter = TownContainers.findAllContainersMatching(
                e,
                item -> true
        ).iterator();

        boolean changes = checkForContainerChanges(e, level, matchIter);
        profileTick(start);

        return changes;
    }

    /**
     * The town-wide container scan walks every recipe-matched room's contained blocks (twice — once
     * for chests, once for block entities) and hashes each container's contents. Measured at 187us
     * per call in a 16-room town and scaling roughly linearly with room count, it was previously
     * paid on <em>every</em> game tick, above the flag-tick throttle — 44% of all tick time, and the
     * one cost that grows as the player builds.
     *
     * <p>Skipping a scan only delays change <em>detection</em>: {@link #checkForContainerChanges}
     * diffs against {@code listenedBlocks}, so a change made during a skipped tick is still caught
     * by the next scan. The visible effect is that item quests and economics update up to
     * {@code ContainerScanInterval} ticks later.
     */
    private boolean isDueForContainerScan() {
        ticksSinceContainerScan++;
        if (ticksSinceContainerScan < Config.CONTAINER_SCAN_INTERVAL.get()) {
            return false;
        }
        ticksSinceContainerScan = 0;
        return true;
    }

    MCTownState warp(
            TownFlagBlockEntity e,
            CompoundTag flagTag,
            ServerLevel level,
            long timeSinceWake
    ) {
        long levelDayTime = level.getDayTime();

        // Snapshot container contents before warp for summary
        Map<ResourceLocation, Integer> beforeItems = snapshotContainerItems(e, level);

        MCTownState newState = null;
        try {
            newState = TownFlagState.advanceTime(parent, level, timeSinceWake);
            if (newState != null) {
                e.getDebugLogger(QT.FLAG_LOGGER, DebugLogArgument.TIME_WARP).log("Storing state on {}: {}", e.getUUID(), newState);
                Compat.getBlockStoredTagData(e).put(NBT_TOWN_STATE, TownStateSerializer.INSTANCE.store(newState));
                TownFlagState.recoverMobs(parent, level);
                TownFlagState.restoreWarpVisuals(level, newState);
                parent.getKnowledgeHandle().registerFoundLoots(newState.knowledge());

                sendWarpSummary(e, level, timeSinceWake, beforeItems);
            }
        } catch (Exception ex) {
            if (Config.CRASH_ON_FAILED_WARP.get()) {
                throw ex;
            }
            QT.FLAG_LOGGER.error("Time warp raised exception", ex);
            QT.FLAG_LOGGER.info("Due to config, continuing as if nothing happened in town while player was away");
        }
        // TODO: Make sure chests get filled/empty
        flagTag.putLong(NBT_TIME_WARP_REFERENCE_TICK, levelDayTime);
        return newState;
    }

    private static void restoreWarpVisuals(ServerLevel level, MCTownState state) {
        ImmutableList<JobPhaseModifier> rules = SpecialRulesRegistry.getAllInstances();
        for (var blockEntry : state.workStates.entrySet()) {
            if (blockEntry.getValue().processingState() == 0) {
                continue;
            }
            BlockPos workBlock = blockEntry.getKey();
            net.minecraft.world.entity.LivingEntity nearest = findNearestVillager(level, state, workBlock);
            if (nearest == null) {
                continue;
            }
            for (JobPhaseModifier rule : rules) {
                rule.afterWarpRecovery(level, nearest, workBlock);
            }
        }
    }

    private static net.minecraft.world.entity.LivingEntity findNearestVillager(
            ServerLevel level,
            MCTownState state,
            BlockPos workBlock
    ) {
        net.minecraft.world.entity.LivingEntity nearest = null;
        double nearestDist = Double.MAX_VALUE;
        for (TownState.VillagerData<?> vData : state.villagers) {
            net.minecraft.world.entity.Entity entity = level.getEntity(vData.uuid);
            if (!(entity instanceof net.minecraft.world.entity.LivingEntity living)) {
                continue;
            }
            double dist = workBlock.distSqr(new BlockPos(
                    (int) vData.xPosition, (int) vData.yPosition, (int) vData.zPosition
            ));
            if (dist < nearestDist) {
                nearestDist = dist;
                nearest = living;
            }
        }
        return nearest;
    }

    private Map<ResourceLocation, Integer> snapshotContainerItems(TownFlagBlockEntity e, ServerLevel level) {
        Map<ResourceLocation, Integer> snapshot = new HashMap<>();
        ImmutableList<MCTownItem> allStacks = TownContainers.getAllStacks(e, level);
        for (MCTownItem stack : allStacks) {
            if (stack.isEmpty()) {
                continue;
            }
            ResourceLocation id = Compat.getItemId(stack.get());
            snapshot.merge(id, stack.toMCItemStack().getCount(), Integer::sum);
        }
        return snapshot;
    }

    private void sendWarpSummary(
            TownFlagBlockEntity e,
            ServerLevel level,
            long timeSinceWake,
            Map<ResourceLocation, Integer> beforeItems
    ) {
        // Only show summary for meaningful absences (at least half a MC day)
        if (timeSinceWake < 12000) {
            return;
        }

        Map<ResourceLocation, Integer> afterItems = snapshotContainerItems(e, level);

        // Compute deltas (new items produced)
        Map<ResourceLocation, Integer> deltas = new HashMap<>();
        for (Map.Entry<ResourceLocation, Integer> entry : afterItems.entrySet()) {
            int before = beforeItems.getOrDefault(entry.getKey(), 0);
            int delta = entry.getValue() - before;
            if (delta > 0) {
                deltas.put(entry.getKey(), delta);
            }
        }

        if (deltas.isEmpty()) {
            return;
        }

        long daysAway = timeSinceWake / 24000;
        String dayStr = daysAway == 1 ? "1 day" : daysAway + " days";

        StringBuilder items = new StringBuilder();
        int count = 0;
        for (Map.Entry<ResourceLocation, Integer> entry : deltas.entrySet()) {
            if (count > 0) {
                items.append(", ");
            }
            String itemName = entry.getKey().getPath().replace("_", " ");
            items.append(entry.getValue()).append(" ").append(itemName);
            count++;
            if (count >= 5) {
                int remaining = deltas.size() - 5;
                if (remaining > 0) {
                    items.append(", and ").append(remaining).append(" more");
                }
                break;
            }
        }

        String message = "While you were away (" + dayStr + "): " + items;
        e.messages.broadcastMessage(message);

        // Fire FirstWarp advancement if away for 1+ MC day
        if (daysAway >= 1) {
            AdvancementsInit.TUTORIAL_TRIGGER.triggerForNearestPlayer(
                    level, TutorialTrigger.Triggers.FirstWarp, e.getBlockPos()
            );
        }
    }

    private void profileTick(long startTime) {
        if (Config.TICK_SAMPLING_RATE.get() > 0) {
            long end = System.currentTimeMillis();
            times.add((int) (end - startTime));

            if (times.size() > Config.TICK_SAMPLING_RATE.get()) {
                Questown.LOGGER.debug(
                        "[TownFlagState] Average tick length: {}",
                        times.stream().mapToInt(Integer::intValue).average()
                );
                times.clear();
            }
        }
    }

    private boolean checkForContainerChanges(
            TownFlagBlockEntity e,
            ServerLevel level,
            Iterator<ContainerTarget<MCContainer, MCTownItem>> matchIter
    ) {
        boolean containersChanged = false;

        for (int i = 0; i < Config.BASE_MAX_LOOP.get(); i++) {
            if (!matchIter.hasNext()) {
                break;
            }
            ContainerTarget<MCContainer, MCTownItem> v = matchIter.next();
            BlockPos bp = Positions.ToBlock(v.getPosition(), v.getYPosition());
            BlockEntity entity = level.getBlockEntity(bp);
            if (entity == null) {
                QT.FLAG_LOGGER.error("Entity is null at {}, but was expected to be a container", bp);
                continue;
            }
            LazyOptional<IItemHandler> cap = entity.getCapability(Compat.itemHandler());
            if (!cap.isPresent()) {
                continue;
            }
            int newValue = determineValue(cap.resolve().get());
            if (listenedBlocks.containsKey(bp)) {
                Integer oldValue = listenedBlocks.get(bp);
                if (!oldValue.equals(newValue)) {
                    e.getDebugLogger(QT.FLAG_LOGGER, DebugLogArgument.TOWN_STATE_CHANGES).log("Chest tags changed");
                    containersChanged = true;
                } else {
                    continue;
                }
            } else {
                containersChanged = true;
            }
            listenedBlocks.put(bp, newValue);
        }
        return containersChanged;
    }

    private static int determineValue(IItemHandler cap) {
        ArrayList<String> itemNames = new ArrayList<>(cap.getSlots());
        for (int i = 0; i < cap.getSlots(); i++) {
            itemNames.add(cap.getStackInSlot(i).toString());
        }
        return itemNames.hashCode();
    }

    void putStateOnTile(
            CompoundTag flagTag,
            UUID uuid,
            TownInterface.DebugLogger logger
    ) {
        @Nullable MCTownState state = captureState();
        if (state == null) {
            QT.FLAG_LOGGER.warn("TownState was null. Will not store.");
            return;
        }
        logger.log("[Tile] Storing state on {}: {}", uuid, state);
        CompoundTag cereal = TownStateSerializer.INSTANCE.store(state);
        flagTag.put(NBT_TOWN_STATE, cereal);
    }
}
