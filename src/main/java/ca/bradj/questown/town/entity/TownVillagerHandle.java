package ca.bradj.questown.town.entity;

import ca.bradj.questown.QT;
import ca.bradj.questown.core.VillagerUUID;
import ca.bradj.questown.jobs.JobID;
import ca.bradj.questown.jobs.ServerJobsRegistry;
import ca.bradj.questown.mobs.visitor.VisitorMobEntity;
import ca.bradj.questown.town.Effect;
import ca.bradj.questown.town.TownVillagerUIs;
import ca.bradj.questown.town.UnsafeTown;
import ca.bradj.questown.town.interfaces.VillagerHolder;
import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableMap;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

public class TownVillagerHandle implements VillagerHolder, SimpleVillagerHandle.Delegator<VisitorMobEntity> {

    public static final TownVillagerHandlerSerializer SERIALIZER = new TownVillagerHandlerSerializer();

    // Unserialized
    private final Map<VillagerUUID, LookTarget> lookTargets = new HashMap<>();
    private final Map<VillagerUUID, LookTarget> mostRecentLookTarget = new HashMap<>();

    private final UnsafeTown town = new UnsafeTown(getClass());
    private final SimpleVillagerHandle<Object, VisitorMobEntity> delegate;
    private final TownVillagerUIsHandle uis;

    public TownVillagerHandle() {
        this.delegate = new SimpleVillagerHandle<>(this);
        this.uis = new TownVillagerUIsHandle(delegate);
    }


    public void associate(TownFlagBlockEntity t) {
        this.town.initialize(t);
        delegate.associate(t);
        uis.init(t);
    }

    public void initialize(
            Map<UUID, Integer> fullness,
            Map<UUID, ? extends ImmutableCollection<Effect>> moodEffects,
            Map<UUID, Integer> damage,
            Map<UUID, ? extends ImmutableCollection<JobID>> unlockedJobs,
            Map<UUID, ? extends Map<JobID, ? extends ImmutableCollection<JobID>>> jobsKnownToExist,
            ImmutableMap<UUID, Integer> experience,
            ImmutableMap<UUID, Integer> level,
            ImmutableMap<VillagerUUID, Boolean> jobChangesPending
    ) {
        delegate.initialize(fullness, moodEffects, damage, unlockedJobs, jobsKnownToExist, experience, level, jobChangesPending);
    }

    @Override
    public void recallVillagers() {
        final BlockPos visitorJoinPos = town.getUnsafe().getBlockPos();
        delegate.forEach(v -> {
            QT.FLAG_LOGGER.info("Moving {} to {} and healing", v, visitorJoinPos);
            v.setPos(visitorJoinPos.getX(), visitorJoinPos.getY(), visitorJoinPos.getZ());
            v.setHealth(v.getMaxHealth());
        });
    }

    @Override
    public JobID getJobId(VisitorMobEntity vEntity) {
        return vEntity.getJobId();
    }

    @Override
    public UUID getUUID(VisitorMobEntity v) {
        return v.getUUID();
    }

    @Override
    public void setJob(
            UUID visitorUUID,
            JobID jobName,
            VisitorMobEntity f
    ) {
        f.setJob(ServerJobsRegistry.getInitializedJob(
                town.getServerLevelUnsafe(),
                jobName,
                f.getJobJournalSnapshot().items(),
                visitorUUID
        ));
    }

    @Override
    public void setChanged() {
        town.getUnsafe().setChanged();
    }

    @Override
    public void addSleepListener(Consumer<VisitorMobEntity> listener) {
        listener.accept();
    }

    @Override
    public void claimBed(VisitorMobEntity vEntity) {

    }

    private record LookTarget(
            Entity who,
            Long untilTick
    ) {
    }
}
