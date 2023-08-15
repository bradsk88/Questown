package ca.bradj.questown.town;

import ca.bradj.questown.Questown;
import ca.bradj.questown.blocks.TownFlagBlock;
import ca.bradj.questown.integration.minecraft.MCContainer;
import ca.bradj.questown.integration.minecraft.MCTownItem;
import ca.bradj.questown.integration.minecraft.TownStateSerializer;
import ca.bradj.questown.jobs.GathererJournal;
import ca.bradj.questown.jobs.GathererTimeWarper;
import ca.bradj.questown.mobs.visitor.ContainerTarget;
import ca.bradj.questown.mobs.visitor.VisitorMobEntity;
import ca.bradj.questown.mobs.visitor.VisitorMobJob;
import ca.bradj.roomrecipes.adapter.Positions;
import com.google.common.base.Function;
import com.google.common.collect.ImmutableList;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.items.CapabilityItemHandler;
import net.minecraftforge.items.IItemHandler;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class TownFlagState {
    static final String NBT_LAST_TICK = String.format("%s_last_tick", Questown.MODID);
    static final String NBT_TOWN_STATE = String.format("%s_town_state", Questown.MODID);
    private final TownFlagBlockEntity parent;
    private boolean initialized = false;
    private final Stack<Function<TownFlagBlockEntity, TownState<MCContainer, MCTownItem>>> townInit = new Stack<>();

    private final Map<BlockPos, Integer> listenedBlocks = new HashMap<>();

    public TownFlagState(TownFlagBlockEntity parent) {
        this.parent = parent;
    }


    @Nullable TownState<MCContainer, MCTownItem> captureState() {
        ImmutableList.Builder<TownState.VillagerData<MCTownItem>> vB = ImmutableList.builder();
        for (LivingEntity entity : parent.entities) {
            if (entity instanceof VisitorMobEntity) {
                if (!((VisitorMobEntity) entity).isInitialized()) {
                    return null;
                }
                Vec3 pos = entity.position();
                TownState.VillagerData<MCTownItem> data = new TownState.VillagerData<>(
                        pos.x, pos.y, pos.z,
                        ((VisitorMobEntity) entity).getJobJournalSnapshot(),
                        entity.getUUID()
                );
                vB.add(data);
            }
        }

        long dayTime = parent.getServerLevel().getDayTime();
        TownState<MCContainer, MCTownItem> ts = new TownState<>(
                vB.build(),
                TownContainers.findAllMatching(parent, item -> true).toList(),
                parent.getWelcomeMats(),
                dayTime
        );
        return ts;
    }

    static TownState<MCContainer, MCTownItem> advanceTime(
            TownFlagBlockEntity e,
            ServerLevel sl
    ) {
        long dayTime = sl.getDayTime();
        if (e.advancedTimeOnTick == dayTime) {
            Questown.LOGGER.debug("Already advanced time on this tick. Skipping.");
            return null;
        }

        e.advancedTimeOnTick = dayTime;

        TownState<MCContainer, MCTownItem> storedState;
        if (e.getTileData().contains(NBT_TOWN_STATE)) {
            storedState = TownStateSerializer.INSTANCE.load(
                    e.getTileData().getCompound(NBT_TOWN_STATE),
                    sl, bp -> e.getWelcomeMats().contains(bp)
            );
            Questown.LOGGER.debug("Loaded state from NBT: {}", storedState);
        } else {
            storedState = new TownState<>(ImmutableList.of(), ImmutableList.of(), ImmutableList.of(), 0);
            Questown.LOGGER.warn("NBT had no town state. That's probably a bug. Town state will reset");
        }

        ArrayList<TownState.VillagerData<MCTownItem>> villagers = new ArrayList<>(storedState.villagers);

        long ticksPassed = dayTime - storedState.worldTimeAtSleep;
        if (ticksPassed == 0) {
            Questown.LOGGER.debug("Time warp is not applicable");
            return storedState;
        }
        GathererTimeWarper<MCTownItem> warper = new GathererTimeWarper<MCTownItem>(
                storedState,
                (max) -> VisitorMobJob.getLootFromLevel(sl, max),
                storedState,
                () -> new MCTownItem(Items.AIR)
        );

        for (int i = 0; i < villagers.size(); i++) {
            TownState.VillagerData<MCTownItem> v = villagers.get(i);
            GathererJournal.Snapshot<MCTownItem> unwarped = v.journal;
            Questown.LOGGER.debug("[{}] Warping time by {} ticks, starting with journal: {}", v.uuid, ticksPassed, storedState);
            GathererJournal.Snapshot<MCTownItem> warped = warper.timeWarp(
                    unwarped,
                    dayTime,
                    ticksPassed,
                    v.getCapacity()
            );
            Questown.LOGGER.debug("[{}] Warping complete, journal is now: {}", v.uuid, storedState);
            villagers.set(i, new TownState.VillagerData<>(v.xPosition, v.yPosition, v.zPosition, warped, v.uuid));
        }

        return new TownState<>(villagers, storedState.containers, storedState.gates, dayTime);
    }

    static void recoverMobs(
            TownFlagBlockEntity e,
            ServerLevel sl
    ) {
        ImmutableList<LivingEntity> entitiesSnapshot = ImmutableList.copyOf(e.entities);
        for (LivingEntity entity : entitiesSnapshot) {
            e.entities.remove(entity);
            entity.remove(Entity.RemovalReason.DISCARDED);
        }

        if (e.getTileData().contains(NBT_TOWN_STATE)) {
            TownState<MCContainer, MCTownItem> storedState = TownStateSerializer.INSTANCE.load(
                    e.getTileData().getCompound(NBT_TOWN_STATE),
                    sl, bp -> e.getWelcomeMats().contains(bp)
            );
            for (TownState.VillagerData<MCTownItem> v : storedState.villagers) {
                VisitorMobEntity recovered = new VisitorMobEntity(sl, e);
                recovered.initialize(
                        v.uuid,
                        v.xPosition,
                        v.yPosition,
                        v.zPosition,
                        v.journal
                );
                sl.addFreshEntity(recovered);
                e.registerEntity(recovered);
            }
            Questown.LOGGER.debug("Loaded state from NBT: {}", storedState);
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
        long lastTick = flagTag.getLong(NBT_LAST_TICK);
        long gt = level.getDayTime();
        long timeSinceWake = gt - lastTick;
        boolean waking = timeSinceWake > 10 || !initialized;
        this.initialized = true;
        flagTag.putLong(NBT_LAST_TICK, gt);

        if (waking) {
            Questown.LOGGER.debug(
                    "Recovering villagers due to player return (last near {} ticks ago [now {}, then {}])",
                    timeSinceWake, gt, lastTick
            );
            TownState<MCContainer, MCTownItem> newState = TownFlagState.advanceTime(parent, level);
            TownFlagState.recoverMobs(parent, level);
            Questown.LOGGER.debug("Storing state on {}: {}", e.getUUID(), newState);
            e.getTileData().put(NBT_TOWN_STATE, TownStateSerializer.INSTANCE.store(newState));
            // TODO: Make sure chests get filled/empty
        }

        // TODO: Run less often?
        Iterator<ContainerTarget<MCContainer, MCTownItem>> matchIter = TownContainers.findAllMatching(
                e,
                item -> true
        ).iterator();

        return checkForContainerChanges(level, matchIter);
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
        @Nullable TownState<MCContainer, MCTownItem> state = captureState();
        if (state == null) {
            Questown.LOGGER.warn("TownState was null. Will not store.");
            return;
        }
        Questown.LOGGER.debug("Storing state on {}: {}", uuid, state);
        CompoundTag cereal = TownStateSerializer.INSTANCE.store(state);
        flagTag.put(NBT_TOWN_STATE, cereal);
    }
}
