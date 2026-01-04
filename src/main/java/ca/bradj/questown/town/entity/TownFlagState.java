package ca.bradj.questown.town.entity;

import ca.bradj.questown.QT;
import ca.bradj.questown.Questown;
import ca.bradj.questown.commands.DebugLogArgument;
import ca.bradj.questown.core.Config;
import ca.bradj.questown.core.UtilClean;
import ca.bradj.questown.core.VillagerUUID;
import ca.bradj.questown.integration.minecraft.*;
import ca.bradj.questown.jobs.ImmutableSnapshot;
import ca.bradj.questown.jobs.JobID;
import ca.bradj.questown.jobs.ServerJobsRegistry;
import ca.bradj.questown.jobs.Signals;
import ca.bradj.questown.jobs.declarative.DowntimeWork;
import ca.bradj.questown.jobs.leaver.ContainerTarget;
import ca.bradj.questown.mc.Compat;
import ca.bradj.questown.mc.Util;
import ca.bradj.questown.mobs.visitor.VisitorMobEntity;
import ca.bradj.questown.town.PossibilitySources;
import ca.bradj.questown.town.TownContainers;
import ca.bradj.questown.town.TownState;
import ca.bradj.questown.town.Warper;
import ca.bradj.questown.town.interfaces.TownInterface;
import ca.bradj.roomrecipes.adapter.Positions;
import com.google.common.base.Function;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
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

// This class is NOT encapsulated from MC

public class TownFlagState {
    static final String NBT_TIME_WARP_REFERENCE_TICK = String.format("%s_last_tick", Questown.MODID);
    static final String NBT_TOWN_STATE = String.format("%s_town_state", Questown.MODID);
    private final TownFlagBlockEntity parent;
    private boolean initialized = false;
    private final Stack<Function<TownFlagBlockEntity, MCTownState>> townInit = new Stack<>();

    private final Map<BlockPos, Integer> listenedBlocks = new HashMap<>();
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
                        pos.x, pos.y, pos.z, snapshot, entity.getUUID()
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
        long gameTick = Util.getTick(sl);
        if (e.advancedTimeOnTick == gameTick) { // TODO[Warp]: Plus or minus some ticks?
            e.getDebugLogger(QT.FLAG_LOGGER, DebugLogArgument.TIME_WARP)
             .log("Already advanced time on this tick. Skipping.");
            return null;
        }

        e.advancedTimeOnTick = gameTick;

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


        ArrayList<TownState.VillagerData<MCHeldItem>> villagers = new ArrayList<>(storedState.villagers);

        long ticksPassed = gameTick - storedState.worldTimeAtSleep;
        if (optionalWarpDuration != null) {
            ticksPassed = optionalWarpDuration;
        }
        if (ticksPassed <= 0) {
            QT.FLAG_LOGGER.info("Time warp is not applicable");
            return storedState;
        }

        ticksPassed = Math.min(ticksPassed, Config.TIME_WARP_MAX_TICKS.get());

        MCTownState liveState = storedState;

        final List<Map.Entry<Long, Function<MCTownState, MCTownState>>> warpSteps = new ArrayList<>();

        Work w = new Work() {
            @Override
            public void recomputeNow() {
                e.getStartableWork().recomputeNow(sl, PossibilitySources.from(e));
            }

            @Override
            public @Nullable JobID getRandomFinishableWork(
                    JobID jobID,
                    Signals.DayTime dayTime
            ) {
                return e.getRandomFinishableWork(jobID, dayTime, false);
            }

            @Override
            public long getTotalDuration(
                    JobID jobID,
                    VillagerUUID vuid
            ) {
                return ServerJobsRegistry.getUninitializedJob(jobID, vuid).getJob().getTotalDuration();
            }
        };

        ImportantTicks.Config cfg = new ImportantTicks.Config(Config.MAX_DOWNTIME_TICKS.get());
        for (int i = 0; i < villagers.size(); i++) {
            TownState.VillagerData<MCHeldItem> v = villagers.get(i);
            e.getDebugLogger(QT.FLAG_LOGGER, DebugLogArgument.TIME_WARP).log(
                    "[{}] Warping time by {} ticks, starting with journal: {}",
                    UtilClean.truncateMiddle(v.uuid),
                    ticksPassed,
                    liveState
            );
            ImmutableList<Warper.Tick> ticks = ImportantTicks.forVillager(
                    w, v.getVUID(), v.journal.jobId(), DowntimeWork::matches, cfg, ticksPassed, gameTick
            );
            int ii = i;
            Warper<ServerLevel, MCTownState> vWarper = ServerJobsRegistry.getWarper(i, v.journal.jobId());
            ticks.stream().map(tick -> new AbstractMap.SimpleEntry<>(
                    tick.tick(),
                    (Function<MCTownState, MCTownState>) ts -> vWarper.warp(sl, ts, tick.tick(), tick.ticksSincePrevious(), ii)
            )).forEach(warpSteps::add);
        }

        warpSteps.sort(Map.Entry.comparingByKey());
        // TODO: Return a collection of lambdas that process chunks of 500?
        long before = System.currentTimeMillis();

        for (Map.Entry<Long, Function<MCTownState, MCTownState>> warpStep : warpSteps) {
            MCTownState affectedState = warpStep.getValue().apply(liveState);
            if (affectedState != null) {
                liveState = affectedState;
            }
        }

        long after = System.currentTimeMillis();

        TownInterface.DebugLogger logger =
                Config.LOG_WARP_RESULT.get() ?
                        QT.FLAG_LOGGER::info :
                        e.getDebugLogger(QT.FLAG_LOGGER, DebugLogArgument.TIME_WARP);
        logger.log("State after warp of {}: {}", ticksPassed, liveState);
        QT.FLAG_LOGGER.info("Warp took {} milliseconds", after - before);

        return new MCTownState(
                liveState.villagers,
                liveState.containers,
                liveState.workStates,
                liveState.workTimers,
                liveState.gates,
                liveState.knowledge(),
                liveState.blocksOfProgress,
                gameTick
        );
    }

    public interface Work {
        void recomputeNow();
        @Nullable JobID getRandomFinishableWork(
                JobID jobID,
                Signals.DayTime dayTime
        );

        long getTotalDuration(
                JobID jobID,
                VillagerUUID vuid
        );
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
            }
            e.getDebugLogger(QT.FLAG_LOGGER, DebugLogArgument.TIME_WARP)
             .log("Loaded villager state from NBT: {}", villagers);
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

        // TODO[Performance]: Run less often?
        Iterator<ContainerTarget<MCContainer, MCTownItem>> matchIter = TownContainers.findAllContainersMatching(
                e,
                item -> true
        ).iterator();

        boolean changes = checkForContainerChanges(e, level, matchIter);
        profileTick(start);

        return changes;
    }

    void warp(
            TownFlagBlockEntity e,
            CompoundTag flagTag,
            ServerLevel level,
            long timeSinceWake
    ) {
        long levelDayTime = level.getDayTime();
        try {
            MCTownState newState = TownFlagState.advanceTime(parent, level, timeSinceWake);
            if (newState != null) {
                e.getDebugLogger(QT.FLAG_LOGGER, DebugLogArgument.TIME_WARP)
                 .log("Storing state on {}: {}", e.getUUID(), newState);
                Compat.getBlockStoredTagData(e).put(NBT_TOWN_STATE, TownStateSerializer.INSTANCE.store(newState));
                TownFlagState.recoverMobs(parent, level);
                parent.getKnowledgeHandle().registerFoundLoots(newState.knowledge());
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
            LazyOptional<IItemHandler> cap = entity.getCapability(Compat.ITEM_HANDLER);
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
