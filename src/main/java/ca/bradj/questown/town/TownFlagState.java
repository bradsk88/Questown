package ca.bradj.questown.town;

import ca.bradj.questown.QT;
import ca.bradj.questown.Questown;
import ca.bradj.questown.core.Config;
import ca.bradj.questown.integration.minecraft.*;
import ca.bradj.questown.jobs.*;
import ca.bradj.questown.jobs.leaver.ContainerTarget;
import ca.bradj.questown.mobs.visitor.VisitorMobEntity;
import ca.bradj.roomrecipes.adapter.Positions;
import com.google.common.base.Function;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.items.CapabilityItemHandler;
import net.minecraftforge.items.IItemHandler;
import org.jetbrains.annotations.Nullable;

import java.util.*;

// This class is NOT encapsulated from MC

public class TownFlagState {
    static final String NBT_LAST_TICK = String.format("%s_last_tick", Questown.MODID);
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
        for (LivingEntity entity : parent.entities) {
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
        MCTownState ts = new MCTownState(
                vB.build(),
                TownContainers.findAllMatching(parent, item -> true).toList(),
                // TODO[ASAP]: Store statuses for all villagers
                parent.getWorkStatusHandle(null).getAll(),
                parent.getWelcomeMats(),
                dayTime
        );
        return ts;
    }

    static MCTownState advanceTime(
            TownFlagBlockEntity e,
            ServerLevel sl
    ) {
        long dayTime = sl.getDayTime();
        if (e.advancedTimeOnTick == dayTime) {
            Questown.LOGGER.debug("Already advanced time on this tick. Skipping.");
            return null;
        }

        e.advancedTimeOnTick = dayTime;

        MCTownState storedState;
        if (e.getPersistentData().contains(NBT_TOWN_STATE)) {
            storedState = TownStateSerializer.INSTANCE.load(
                    e.getPersistentData().getCompound(NBT_TOWN_STATE),
                    sl, bp -> e.getWelcomeMats().contains(bp)
            );
            QT.FLAG_LOGGER.trace("Loaded state from NBT: {}", storedState);
        } else {
            storedState = new MCTownState(
                    ImmutableList.of(),
                    ImmutableList.of(),
                    ImmutableMap.of(),
                    ImmutableList.of(),
                    0
            );
            QT.FLAG_LOGGER.warn("NBT had no town state. That's probably a bug. Town state will reset");
        }


        ArrayList<TownState.VillagerData<MCHeldItem>> villagers = new ArrayList<>(storedState.villagers);

        long ticksPassed = dayTime - storedState.worldTimeAtSleep;
        if (ticksPassed == 0) {
            QT.FLAG_LOGGER.debug("Time warp is not applicable");
            return storedState;
        }

        GathererTimeWarper.LootGiver<MCTownItem, MCHeldItem, ResourceLocation> loot =
                (int max, GathererJournal.Tools tools, ResourceLocation biome) -> GathererJob.getLootFromLevel(e, max, tools, biome);
        MCTownState liveState = storedState;

        for (int i = 0; i < villagers.size(); i++) {
            TownState.VillagerData<MCHeldItem> v = villagers.get(i);
            Snapshot<MCHeldItem> unwarped = v.journal;
            QT.FLAG_LOGGER.trace("[{}] Warping time by {} ticks, starting with journal: {}", v.uuid, ticksPassed, liveState);
            Warper<MCTownState> vWarper = JobsRegistry.getWarper(
                    i, v.journal.jobId()
            );
            // TODO[ASAP]: Stop passing ticks in.
            //  The way it works right now, each villager works through the passed
            //  ticks as though they were the only villager present, and then we
            //  move on to the next one who repeats the process. What we should
            //  probably do is warp one chunk of time for each of the villagers
            //  until we have reached the desired number of ticks. That better
            //  simulates a village full of people (and we can maybe even run
            //  each "chunk" on a separate game tick.
            liveState = vWarper.warp(liveState, dayTime, ticksPassed, i);
        }

        return new MCTownState(
                liveState.villagers,
                liveState.containers,
                liveState.workStates,
                liveState.gates,
                dayTime
        );
    }

    static void recoverMobs(
            TownFlagBlockEntity e,
            ServerLevel sl
    ) {
        ImmutableList<LivingEntity> entitiesSnapshot = ImmutableList.copyOf(e.entities);
        for (LivingEntity entity : entitiesSnapshot) {
            e.entities.remove(entity);
            entity.stopSleeping();
            entity.remove(Entity.RemovalReason.DISCARDED);
        }

        if (e.getPersistentData().contains(NBT_TOWN_STATE)) {
            MCTownState storedState = TownStateSerializer.INSTANCE.load(
                    e.getPersistentData().getCompound(NBT_TOWN_STATE),
                    sl, bp -> e.getWelcomeMats().contains(bp)
            );
            for (TownState.VillagerData<MCHeldItem> v : storedState.villagers) {
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
                e.registerEntity(recovered);
            }
            QT.LOGGER.trace("Loaded state from NBT: {}", storedState);
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
    public boolean tick(TownFlagBlockEntity e, CompoundTag flagTag, ServerLevel level) {
        long start = System.currentTimeMillis();
        long lastTick = flagTag.getLong(NBT_LAST_TICK);
        long gt = level.getDayTime();
        long timeSinceWake = gt - lastTick;
        boolean waking = timeSinceWake > 10 || !initialized;
        this.initialized = true;

        if (waking && e.isInitialized()) {
            QT.FLAG_LOGGER.debug(
                    "Recovering villagers due to player return (last near {} ticks ago [now {}, then {}])",
                    timeSinceWake, gt, lastTick
            );
            MCTownState newState = TownFlagState.advanceTime(parent, level);
            if (newState != null) {
                TownFlagState.recoverMobs(parent, level);
                Questown.LOGGER.trace("Storing state on {}: {}", e.getUUID(), newState);
                e.getPersistentData().put(NBT_TOWN_STATE, TownStateSerializer.INSTANCE.store(newState));
            }
            // TODO: Make sure chests get filled/empty
            flagTag.putLong(NBT_LAST_TICK, gt);
        } else {
            flagTag.putLong(NBT_LAST_TICK, gt);
        }

        // TODO: Run less often?
        Iterator<ContainerTarget<MCContainer, MCTownItem>> matchIter = TownContainers.findAllMatching(
                e,
                item -> true
        ).iterator();

        boolean changes = checkForContainerChanges(level, matchIter);
        profileTick(start);

        return changes;
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
            ServerLevel level,
            Iterator<ContainerTarget<MCContainer, MCTownItem>> matchIter
    ) {
        boolean containersChanged = false;

        while (matchIter.hasNext()) {
            ContainerTarget<MCContainer, MCTownItem> v = matchIter.next();
            BlockPos bp = Positions.ToBlock(v.getPosition(), v.getYPosition());
            BlockEntity entity = level.getBlockEntity(bp);
            LazyOptional<IItemHandler> cap = entity.getCapability(
                    CapabilityItemHandler.ITEM_HANDLER_CAPABILITY);
            if (cap != null && cap.isPresent()) {
                int newValue = determineValue(cap.resolve().get());
                if (listenedBlocks.containsKey(bp)) {
                    Integer oldValue = listenedBlocks.get(bp);
                    if (!oldValue.equals(newValue)) {
                        Questown.LOGGER.debug("Chest tags changed");
                        containersChanged = true;
                    } else {
                        continue;
                    }
                } else {
                    containersChanged = true;
                }
                listenedBlocks.put(bp, newValue);
            }
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

    void putStateOnTile(CompoundTag flagTag, UUID uuid) {
        @Nullable MCTownState state = captureState();
        if (state == null) {
            QT.FLAG_LOGGER.warn("TownState was null. Will not store.");
            return;
        }
        QT.FLAG_LOGGER.trace("[Tile] Storing state on {}: {}", uuid, state);
        CompoundTag cereal = TownStateSerializer.INSTANCE.store(state);
        flagTag.put(NBT_TOWN_STATE, cereal);
    }
}
