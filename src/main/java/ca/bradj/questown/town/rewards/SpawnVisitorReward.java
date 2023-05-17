package ca.bradj.questown.town.rewards;

import ca.bradj.questown.Questown;
import ca.bradj.questown.core.init.EntitiesInit;
import ca.bradj.questown.core.init.RewardsInit;
import ca.bradj.questown.mobs.visitor.VisitorMobEntity;
import ca.bradj.questown.town.interfaces.TownInterface;
import ca.bradj.questown.town.quests.MCReward;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Random;
import java.util.UUID;

public class SpawnVisitorReward extends MCReward {

    public static final String ID = "spawn_visitor_reward";
    private static final String NBT_VISITOR_UUID = "visitor_uuid";
    private final TownInterface town;
    private UUID visitorUUID;

    public SpawnVisitorReward(
            RewardType<? extends MCReward> rType,
            @NotNull TownInterface entity,
            @Nullable UUID visitorUUID
    ) {
        super(rType);
        this.visitorUUID = visitorUUID;
        this.town = entity;
    }

    public SpawnVisitorReward(TownInterface entity) {
        this(entity, null);
    }

    public SpawnVisitorReward(
            TownInterface entity,
            UUID visitorUUID
    ) {
        this(RewardsInit.VISITOR.get(), entity, visitorUUID);
    }

    @Override
    protected @NotNull RewardApplier getApplier() {
        return () -> spawnVisitorNearby(town, visitorUUID);
    }

    private static void spawnVisitorNearby(
            TownInterface entity,
            @Nullable UUID visitorUUID
    ) {
        if (entity.getLevel() == null || !(entity.getLevel() instanceof ServerLevel sl)) {
            return;
        }

        VisitorMobEntity vEntity = new VisitorMobEntity(EntitiesInit.VISITOR.get(), sl, entity);
        vEntity.setPos(entity.getVisitorJoinPos());
        if (visitorUUID != null) {
            vEntity.setUUID(visitorUUID);
        }
        sl.addFreshEntity(vEntity);
        Questown.LOGGER.debug("Spawned visitor {} at {}", vEntity.getUUID(), vEntity.getOnPos());

        entity.generateRandomQuest(sl);
    }

    @Override
    protected Tag serializeNbt() {
        CompoundTag tag = new CompoundTag();
        if (this.visitorUUID != null) {
            tag.putUUID(NBT_VISITOR_UUID, this.visitorUUID);
        }
        return tag;
    }

    @Override
    protected void deserializeNbt(
            TownInterface entity,
            CompoundTag tag
    ) {
        if (tag.contains(NBT_VISITOR_UUID)) {
            this.visitorUUID = tag.getUUID(NBT_VISITOR_UUID);
        }
    }
}
