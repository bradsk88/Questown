package ca.bradj.questown.town.interfaces;

import ca.bradj.questown.core.VillagerUUID;
import ca.bradj.questown.integration.jobs.UnsafeVillagerData;
import ca.bradj.questown.jobs.JobID;
import ca.bradj.questown.mobs.visitor.VisitorMobEntity;
import ca.bradj.questown.town.PoseInPlace;
import ca.bradj.questown.town.VillagerStatsData;
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

/**
 * @deprecated This interface has become a grab-bag of methods. Use more specific interfaces instead.
 */
@Deprecated(forRemoval = true)
public interface VillagerHolder {
    long size();

    void addStatsListener(Consumer<VillagerStatsData> l);

    void removeStatsListener(Consumer<VillagerStatsData> villagerStatsMenu);

    Collection<LivingEntity> entities();

    void remove(LivingEntity entity);

    void showUI(
            ServerPlayer sender,
            String type,
            UUID villagerId
    );

    void fillHunger(UUID uuid);

    void fillHunger(
            UUID uuid,
            float amount
    );

    void makeAngry(UUID uuid);

    boolean isDining(UUID uuid);

    void applyEffect(
            ResourceLocation effect,
            Long expireOnTick,
            UUID uuid
    );

    int getAffectedTime(
            UUID uuid,
            Integer timeToAugment
    );

    int getWorkSpeed(UUID uuid);

    /**
     * The townie's current proficiency level ∈ [0,1] for {@code proficiencyId}, or 0 if
     * they hold no proficiency in it. See ADR-0010.
     */
    float getProficiency(
            UUID uuid,
            String proficiencyId
    );

    /**
     * The townie's full proficiency map (proficiency-id → level). Read seam for autotests
     * and the warp bridge.
     */
    ImmutableMap<String, Float> getProficiencies(UUID uuid);

    /**
     * Force a single proficiency level (test seam + realtime leveling). Creates the map /
     * entry if absent.
     */
    void setProficiency(
            UUID uuid,
            String proficiencyId,
            float level
    );

    /**
     * Replace the townie's whole proficiency map (warp-load bridge).
     */
    void setProficiencies(
            UUID uuid,
            java.util.Map<String, Float> levels
    );

    VillagerStatsData getStats(UUID uuid);

    Collection<JobID> getJobs();

    ImmutableMap<VillagerUUID, JobID> getVillagerJobs();

    void changeJobForVillager(
            VillagerUUID villagerUUID,
            JobID newJob,
            boolean announce
    );

    /**
     * @deprecated Use VillagerUUID version
     */
    @Deprecated(forRemoval = true)
    void changeJobForVillager(
            UUID villagerUUID,
            JobID newJob,
            boolean announce
    );

    boolean canDine(UUID uuid);

    void freezeVillagers(Integer ticks);

    void recallVillagers();

    void validateEntity(VisitorMobEntity visitorMobEntity);

    void addDamage(UUID uuid);

    int getDamageTicksLeft(UUID uuid);

    void requestPose(
            UUID uuid,
            PoseInPlace pose
    );

    Optional<PoseInPlace> getRequestedPose(UUID uuid);

    void clearPoseRequests(UUID uuid);

    void showMultiStatusUI(ServerPlayer sender);

    void showItemJobsUI(
            ServerPlayer sender,
            Ingredient itemToShowJobsFor
    );

    void register(VisitorMobEntity vEntity);

    void unlockJob(
            UUID villagerUUID,
            JobID id
    );

    void addExperience(
            UUID uuid,
            int exp
    );

    boolean hasBlockOfProgress(UUID uuid);

    void clearBlockOfProgress(UUID uuid);

    void scheduleJobRootChange(
            UUID villagerUUID,
            boolean instant
    );

    boolean isUnlocked(JobID jobID);

    UnsafeVillagerData getUnprotectedDataHandle(@Nullable VillagerUUID vuid);

    Optional<Entity> getLookTarget(@Nullable VillagerUUID vuid);

    void setLookTarget(
            @Nullable VillagerUUID vuid,
            Entity entity,
            long untilTick,
            long thenNotUntilTick
    );

    void showJobUI(
            ServerPlayer sender,
            JobID jobToShow
    );

    void setJobChangePending(
            VillagerUUID vuid,
            boolean value
    );

    boolean isJobChangePending(VillagerUUID vuid);

    boolean isStarving(@Nullable VillagerUUID vuid);

    void setStarving(
            @Nullable VillagerUUID vuid,
            boolean b
    );

    boolean gaveUpDiningRecently(VillagerUUID uuid, long currentTick);

    void toggleHunger();
}
