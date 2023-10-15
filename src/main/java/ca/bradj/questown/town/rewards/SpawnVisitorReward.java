package ca.bradj.questown.town.rewards;

import ca.bradj.questown.QT;
import ca.bradj.questown.Questown;
import ca.bradj.questown.core.init.RewardsInit;
import ca.bradj.questown.integration.minecraft.MCHeldItem;
import ca.bradj.questown.jobs.GathererJournal;
import ca.bradj.questown.mobs.visitor.VisitorMobEntity;
import ca.bradj.questown.town.interfaces.TownInterface;
import ca.bradj.questown.town.quests.MCReward;
import com.google.common.collect.ImmutableList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

public class SpawnVisitorReward extends MCReward {

    public static final GathererJournal.Snapshot<MCHeldItem> EMPTY = new GathererJournal.Snapshot<>(
            GathererJournal.Status.IDLE,
            ImmutableList.of(MCHeldItem.Air(),
                    MCHeldItem.Air(),
                    MCHeldItem.Air(),
                    MCHeldItem.Air(),
                    MCHeldItem.Air(),
                    MCHeldItem.Air()
            )
    );

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
        ServerLevel sl = entity.getServerLevel();
        if (sl == null) {
            return;
        }

        VisitorMobEntity vEntity = new VisitorMobEntity(sl, entity);
        @Nullable UUID initUUID = visitorUUID;
        if (initUUID == null) {
            initUUID = vEntity.getUUID();
        }
        Vec3 vjp = entity.getVisitorJoinPos();
        vEntity.initialize(entity, initUUID, vjp.x, vjp.y, vjp.z, EMPTY);
        entity.registerEntity(vEntity);
        sl.addFreshEntity(vEntity);
        QT.QUESTS_LOGGER.debug("Spawned visitor {} at {}", vEntity.getUUID(), vEntity.getOnPos());
    }

    @Override
    public String toString() {
        return "SpawnVisitorReward{" +
                "town=" + town +
                ", visitorUUID=" + visitorUUID +
                '}';
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
