package ca.bradj.questown.town;

import ca.bradj.questown.Questown;
import ca.bradj.questown.integration.minecraft.MCContainer;
import ca.bradj.questown.integration.minecraft.MCTownItem;
import ca.bradj.questown.integration.minecraft.TownStateSerializer;
import ca.bradj.questown.jobs.GathererJournal;
import ca.bradj.questown.jobs.GathererJournals;
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
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Set;
import java.util.Stack;
import java.util.UUID;
import java.util.stream.Collectors;

public class TownFlagState {
    static final String NBT_LAST_TICK = String.format("%s_last_tick", Questown.MODID);
    static final String NBT_TOWN_STATE = String.format("%s_town_state", Questown.MODID);
    private final TownFlagBlockEntity parent;
    private long lastTick = -1;
    private final Stack<Function<ServerLevel, TownState<MCContainer, MCTownItem>>> townInit = new Stack<>();

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
                TownState.VillagerData<MCTownItem> data = new TownState.VillagerData<>(
                        Positions.FromBlockPos(entity.blockPosition()),
                        entity.blockPosition().getY(),
                        ((VisitorMobEntity) entity).getJobJournalSnapshot(),
                        entity.getUUID()
                );
                vB.add(data);
            }
        }

        TownState<MCContainer, MCTownItem> ts = new TownState<>(
                vB.build(),
                TownContainers.findAllMatching(parent, item -> true).toList(),
                parent.getServerLevel().getDayTime()
        );
        return ts;
    }

    static void advanceTime(
            TownFlagBlockEntity e,
            ServerLevel sl
    ) {
        TownState<MCContainer, MCTownItem> storedState;
        if (e.getTileData().contains(NBT_TOWN_STATE)) {
            storedState = TownStateSerializer.INSTANCE.load(
                    e.getTileData().getCompound(NBT_TOWN_STATE),
                    sl
            );
            Questown.LOGGER.debug("Loaded state from NBT: {}", storedState);
        } else {
            storedState = new TownState<>(ImmutableList.of(), ImmutableList.of(), 0);
            Questown.LOGGER.warn("NBT had no town state. That's probably a bug. Town state will reset");
        }

        ArrayList<TownState.VillagerData<MCTownItem>> villagers = new ArrayList<>(storedState.villagers);

        long dayTime = sl.getDayTime();
        long ticksPassed = dayTime - storedState.worldTimeAtSleep;
        for (int i = 0; i < villagers.size(); i++) {
            TownState.VillagerData<MCTownItem> v = villagers.get(i);
            GathererJournal.Snapshot<MCTownItem> unwarped = v.journal;
            GathererJournal.Snapshot<MCTownItem> warped = GathererJournals.timeWarp(
                    unwarped,
                    ticksPassed,
                    storedState,
                    () -> VisitorMobJob.getLootFromLevel(sl, v.getCapacity()),
                    storedState,
                    () -> new MCTownItem(Items.AIR)
            );
            villagers.set(i, new TownState.VillagerData<>(v.position, v.yPosition, warped, v.uuid));
        }

        e.getTileData().put(NBT_TOWN_STATE, TownStateSerializer.INSTANCE.store(
                new TownState<>(villagers, storedState.containers, dayTime)
        ));

        // TODO: Maybe return town state?
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
                    sl
            );
            Set<UUID> uuids = entitiesSnapshot.stream().map(Entity::getUUID).collect(Collectors.toSet());
            for (TownState.VillagerData<MCTownItem> v : storedState.villagers) {
                VisitorMobEntity recovered = new VisitorMobEntity(sl, e);
                recovered.initialize(v.uuid, new BlockPos(v.position.x, v.yPosition, v.position.z), v.journal);
                sl.addFreshEntity(recovered);
                e.registerEntity(recovered);
            }
            Questown.LOGGER.debug("Loaded state from NBT: {}", storedState);
        }
    }

    public void load(CompoundTag tag) {
        if (tag.contains(NBT_LAST_TICK)) {
            this.lastTick = tag.getLong(NBT_LAST_TICK);
        }
        if (tag.contains(NBT_TOWN_STATE)) {
            CompoundTag stateTag = tag.getCompound(NBT_TOWN_STATE);
            this.townInit.push((level) -> TownStateSerializer.INSTANCE.load(stateTag, level));
        }
    }

    public void tick(CompoundTag flagTag, ServerLevel level) {
        long lastTick = flagTag.getLong(NBT_LAST_TICK);
        long timeSinceWake = level.getGameTime() - lastTick;
        boolean waking = timeSinceWake > 10;
        flagTag.putLong(NBT_LAST_TICK, level.getGameTime());

        if (waking) {
            Questown.LOGGER.debug("Recovering villagers due to player return (last near {} ticks ago)", timeSinceWake);
            TownFlagState.advanceTime(parent, level);
            TownFlagState.recoverMobs(parent, level);
            // TODO: Make sure chests get filled/empty
        }

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
