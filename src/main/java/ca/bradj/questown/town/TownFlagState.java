package ca.bradj.questown.town;

import ca.bradj.questown.Questown;
import ca.bradj.questown.integration.minecraft.MCTownItem;
import ca.bradj.questown.integration.minecraft.TownStateSerializer;
import ca.bradj.questown.mobs.visitor.VisitorMobEntity;
import ca.bradj.roomrecipes.adapter.Positions;
import com.google.common.base.Function;
import com.google.common.collect.ImmutableList;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.Set;
import java.util.Stack;
import java.util.UUID;
import java.util.stream.Collectors;

public class TownFlagState {
    static final String NBT_LAST_TICK = String.format("%s_last_tick", Questown.MODID);
    static final String NBT_TOWN_STATE = String.format("%s_town_state", Questown.MODID);
    private final TownFlagBlockEntity parent;
    private long lastTick = -1;
    private final Stack<Function<ServerLevel, TownState<MCTownItem>>> townInit = new Stack<>();

    public TownFlagState(TownFlagBlockEntity parent) {
        this.parent = parent;
    }


    @Nullable TownState<MCTownItem> captureState() {
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

        TownState<MCTownItem> ts = new TownState<>(
                vB.build(),
                TownContainers.findAllMatching(parent, item -> true).toList(),
                parent.getServerLevel().getDayTime()
        );
        return ts;
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
            TownState<MCTownItem> storedState = TownStateSerializer.INSTANCE.load(
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
            TownFlagState.recoverMobs(parent, level);
        }

    }
}
