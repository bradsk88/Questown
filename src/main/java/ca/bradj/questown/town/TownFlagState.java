package ca.bradj.questown.town;

import ca.bradj.questown.Questown;
import ca.bradj.questown.integration.minecraft.MCTownItem;
import ca.bradj.questown.integration.minecraft.TownStateSerializer;
import ca.bradj.questown.mobs.visitor.VisitorMobEntity;
import com.google.common.collect.ImmutableList;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;

import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

public class TownFlagState {
    static final String NBT_TOWN_STATE = String.format("%s_town_state", Questown.MODID);

    static void recoverMobs(
            Level level,
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
                level.addFreshEntity(recovered);
                e.registerEntity(recovered);
            }
            Questown.LOGGER.debug("Loaded state from NBT: {}", storedState);
        }
    }
}
