package ca.bradj.questown.town.entity;

import ca.bradj.questown.core.UtilClean;
import ca.bradj.questown.core.VillagerUUID;
import ca.bradj.questown.integration.jobs.UnsafeVillagerData;
import ca.bradj.questown.jobs.JobID;
import ca.bradj.questown.mobs.visitor.VisitorMobEntity;
import ca.bradj.questown.town.PoseInPlace;
import ca.bradj.questown.town.VillagerStatsData;
import ca.bradj.questown.town.interfaces.VillagerHolder;
import com.google.common.collect.ImmutableMap;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.crafting.Ingredient;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

public class TownVillagerHandles {
    public static VillagerHolder asVillagerHolder(TownVillagerHandle villagerHandle) {
        return new VillagerHolder() {
            @Override
            public long size() {
                return villagerHandle.size();
            }

            @Override
            public void addStatsListener(Consumer<VillagerStatsData> l) {
                villagerHandle.addStatsListener(l);
            }

            @Override
            public void removeStatsListener(Consumer<VillagerStatsData> villagerStatsMenu) {
                villagerHandle.delegate.removeStatsListener(villagerStatsMenu);
            }

            @Override
            public Collection<LivingEntity> entities() {
                return villagerHandle.delegate.entities().stream().map(v -> (LivingEntity) v).toList();
            }

            @Override
            public void remove(LivingEntity entity) {
                if (!(entity instanceof VisitorMobEntity vml)) {
                    return;
                }
                villagerHandle.delegate.remove(vml);
            }

            @Override
            public void showUI(
                    ServerPlayer sender,
                    String type,
                    UUID villagerId
            ) {
                villagerHandle.uis.showUI(sender, type, villagerId);
            }

            @Override
            public void fillHunger(UUID uuid) {
                villagerHandle.delegate.fillHunger(uuid);
            }

            @Override
            public void fillHunger(
                    UUID uuid,
                    float amount
            ) {
                villagerHandle.delegate.fillHunger(uuid, amount);
            }

            @Override
            public void makeAngry(UUID uuid) {
                // TODO: Implement happiness (happy = 100% work speed angry = 50% work speed)
            }

            @Override
            public boolean isDining(UUID uuid) {
                return villagerHandle.delegate.isDining(uuid);
            }

            @Override
            public void applyEffect(
                    ResourceLocation effect,
                    Long expireOnTick,
                    UUID uuid
            ) {
                villagerHandle.applyEffect(effect, expireOnTick, uuid);
            }

            @Override
            public int getAffectedTime(
                    UUID uuid,
                    Integer timeToAugment
            ) {
                return villagerHandle.delegate.getAffectedTime(uuid, timeToAugment);
            }

            @Override
            public int getWorkSpeed(UUID uuid) {
                return villagerHandle.delegate.getWorkSpeed(uuid);
            }

            @Override
            public VillagerStatsData getStats(UUID uuid) {
                return villagerHandle.delegate.getStats(uuid);
            }

            @Override
            public Collection<JobID> getJobs() {
                return villagerHandle.delegate.getJobs();
            }

            @Override
            public ImmutableMap<VillagerUUID, JobID> getVillagerJobs() {
                return UtilClean.mapKeys(villagerHandle.delegate.getVillagerJobs(), VillagerUUID::from);
            }

            @Override
            public void changeJobForVillager(
                    VillagerUUID villagerUUID,
                    JobID newJob,
                    boolean announce
            ) {
                villagerHandle.changeJobForVillager(villagerUUID, newJob, announce);
            }

            @Override
            public void changeJobForVillager(
                    UUID villagerUUID,
                    JobID newJob,
                    boolean announce
            ) {
                long tick = villagerHandle.getTick();
                villagerHandle.delegate.changeJobForVillager(villagerUUID, newJob, tick, announce);
            }

            @Override
            public boolean canDine(UUID uuid) {
                return villagerHandle.delegate.canDine(uuid);
            }

            @Override
            public void freezeVillagers(Integer ticks) {
                villagerHandle.delegate.freezeVillagers(ticks);
            }

            @Override
            public void recallVillagers() {
                villagerHandle.recallVillagers();
            }

            @Override
            public void validateEntity(VisitorMobEntity visitorMobEntity) {
                villagerHandle.delegate.validateEntity(visitorMobEntity);
            }

            @Override
            public void addDamage(UUID uuid) {
                villagerHandle.delegate.addDamage(uuid);
            }

            @Override
            public int getDamageTicksLeft(UUID uuid) {
                return villagerHandle.delegate.getDamageTicksLeft(uuid);
            }

            @Override
            public void requestPose(
                    UUID uuid,
                    PoseInPlace pose
            ) {
                villagerHandle.delegate.requestPose(uuid, pose);
            }

            @Override
            public Optional<PoseInPlace> getRequestedPose(UUID uuid) {
                return villagerHandle.delegate.getRequestedPose(uuid);
            }

            @Override
            public void clearPoseRequests(UUID uuid) {
                villagerHandle.delegate.clearPoseRequests(uuid);
            }

            @Override
            public void showMultiStatusUI(ServerPlayer sender) {
                villagerHandle.uis.showMultiStatusUI(sender, villagerHandle.delegate.entities());
            }

            @Override
            public void showItemJobsUI(
                    ServerPlayer sender,
                    Ingredient itemToShowJobsFor
            ) {
                villagerHandle.uis.showItemJobsUI(sender, itemToShowJobsFor, villagerHandle.delegate.entities());
            }

            @Override
            public void register(VisitorMobEntity vEntity) {
                villagerHandle.register(vEntity);
            }

            @Override
            public void unlockJob(
                    UUID villagerUUID,
                    JobID id
            ) {
                villagerHandle.learning.unlockJob(villagerUUID, id);
            }

            @Override
            public void addExperience(
                    UUID uuid,
                    int exp
            ) {
                villagerHandle.delegate.addExperience(uuid, exp);
            }

            @Override
            public boolean hasBlockOfProgress(UUID uuid) {
                return villagerHandle.hasBlockOfProgress(VillagerUUID.from(uuid));
            }

            @Override
            public void clearBlockOfProgress(UUID uuid) {
                villagerHandle.delegate.clearBlockOfProgress(uuid);
            }

            @Override
            public void scheduleJobRootChange(
                    UUID villagerUUID,
                    boolean instant
            ) {
                long tick = villagerHandle.getTick();
                villagerHandle.delegate.scheduleJobRootChange(villagerUUID, tick, instant);
            }

            @Override
            public boolean isUnlocked(JobID jobID) {
                return villagerHandle.learning.isUnlocked(jobID);
            }

            @Override
            public UnsafeVillagerData getUnprotectedDataHandle(@Nullable VillagerUUID vuid) {
                return villagerHandle.getUnprotectedDataHandle(getUUID(vuid));
            }

            @Override
            public Optional<Entity> getLookTarget(@Nullable VillagerUUID vuid) {
                return villagerHandle.rendering.getLookTarget(getUUID(vuid));
            }

            @Override
            public void setLookTarget(
                    @Nullable VillagerUUID vuid,
                    Entity entity,
                    long untilTick,
                    long thenNotUntilTick
            ) {
                villagerHandle.rendering.setLookTarget(vuid, entity, untilTick, thenNotUntilTick);
            }

            @Override
            public void showJobUI(
                    ServerPlayer sender,
                    JobID jobToShow
            ) {
                villagerHandle.uis.showJobUI(sender, jobToShow, villagerHandle.delegate.entities());
            }

            @Override
            public void setJobChangePending(
                    VillagerUUID vuid,
                    boolean value
            ) {
                villagerHandle.delegate.setJobChangePending(getUUID(vuid), value);
            }

            @Override
            public boolean isJobChangePending(VillagerUUID vuid) {
                return villagerHandle.delegate.isJobChangePending(getUUID(vuid));
            }

            @Override
            public boolean isStarving(@Nullable VillagerUUID vuid) {
                return villagerHandle.delegate.isStarving(getUUID(vuid));
            }

            @Override
            public void setStarving(
                    @Nullable VillagerUUID vuid,
                    boolean b
            ) {
                villagerHandle.delegate.setStarving(getUUID(vuid), b);
            }

            @Override
            public boolean gaveUpDiningRecently(
                    VillagerUUID uuid,
                    long currentTick
            ) {
                return villagerHandle.delegate.gaveUpDiningRecently(getUUID(uuid), currentTick);
            }

            @Override
            public void toggleHunger() {
                villagerHandle.delegate.toggleHunger();
            }

            @SuppressWarnings("removal")
            private static UUID getUUID(VillagerUUID from) {
                return VillagerUUID.get(from);
            }
        };
    }

    public static Collection<JobID> getJobs(TownVillagerHandle villagersHandle) {
        return villagersHandle.delegate.getJobs();
    }
}
