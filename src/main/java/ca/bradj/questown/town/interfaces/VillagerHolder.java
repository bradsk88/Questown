package ca.bradj.questown.town.interfaces;

import ca.bradj.questown.core.VillagerUUID;
import ca.bradj.questown.integration.jobs.UnsafeVillagerData;
import ca.bradj.questown.jobs.JobID;
import ca.bradj.questown.mobs.visitor.VisitorMobEntity;
import ca.bradj.questown.town.PoseInPlace;
import ca.bradj.questown.town.VillagerStatsData;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.crafting.Ingredient;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Map;
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

    /**
     * Same as {@link #showUI(ServerPlayer, String, UUID)} but carries the management-only flag:
     * when {@code showInventoryTab} is false the opened villager menu hides its inventory tab
     * (the flag's villager-card click-through, 2026-10-14 playtest).
     */
    void showUI(
            ServerPlayer sender,
            String type,
            UUID villagerId,
            boolean showInventoryTab
    );

    /**
     * The townie's user-facing display name ("Villager N", the monotonic arrival number;
     * 2026-10-14 playtest), or an empty string when none is assigned yet.
     */
    String displayNameFor(UUID villagerId);

    /**
     * How a chat message names a townie: its display name, or a shortened UUID when it has none.
     * A raw UUID must never reach a translatable message (1.20.6 rejects non-primitive arguments).
     */
    default String labelFor(UUID villagerId) {
        return label(displayNameFor(villagerId), villagerId);
    }

    /** {@link #labelFor}'s rule: the display name, or a shortened UUID when it is empty. */
    static String label(String displayName, UUID villagerId) {
        return displayName.isEmpty() ? ca.bradj.questown.core.UtilClean.truncateMiddle(villagerId) : displayName;
    }

    /**
     * Every assigned display name (villager UUID -&gt; "Villager N"); townies without a number
     * are omitted, so callers fall back to the truncated UUID.
     */
    Map<UUID, String> displayNamesForAll();

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

    /**
     * The townie's villager level (default 1). The skills-tab gate: a job is only visible /
     * unlockable once the townie's level reaches the job's JSON {@code available_at_level}.
     */
    int getVillagerLevel(
            UUID villagerUUID
    );

    void addExperience(
            UUID uuid,
            int exp
    );

    boolean hasBlockOfProgress(UUID uuid);

    void clearBlockOfProgress(UUID uuid);

    /**
     * Schedules a root change (no blessed guarantee) and returns the pre-rolled root decided at
     * the spend moment, or empty when instant or when no candidate root exists. The morning
     * applies it unless a tutorial override wins.
     */
    Optional<JobID> scheduleJobRootChange(
            UUID villagerUUID,
            boolean instant
    );

    /**
     * Same as {@link #scheduleJobRootChange(UUID, boolean)} but carries a guaranteed root
     * (from a blessed "gold" BOP): when present, the pending change takes this root instead
     * of picking randomly at handleMorning.
     */
    Optional<JobID> scheduleJobRootChange(
            UUID villagerUUID,
            boolean instant,
            Optional<JobID> guaranteedRoot
    );

    boolean isUnlocked(JobID jobID);

    /**
     * Per-townie unlocked jobs. The JOB_UNLOCK quest checks the town-wide unlocked set to
     * confirm a producer root is held by no one before suggesting the unlock, and to complete
     * the quest once a root is unlocked by any townie.
     */
    ImmutableMap<VillagerUUID, ImmutableSet<JobID>> getUnlockedJobs();

    /**
     * Whether a specific townie has unlocked this work (per-villager). The town-wide
     * {@link #isUnlocked(JobID)} is the "can anyone in town do this" gate used by the
     * request board; this is the narrower "can *this* townie do this" gate the
     * work-seeker needs, so a freshly-minted townie only sees the works it itself holds.
     */
    boolean isUnlocked(UUID villagerUUID, JobID jobID);

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

    /**
     * True when the townie currently has an assigned bed the town's rooms provide
     * (a bed inside a matched room recipe). Drives the SLEEPLESS debuff report.
     */
    boolean hasBed(VisitorMobEntity v);

    /**
     * True when the given mood effect (e.g. SLEEPLESS after a bedless night) is
     * currently active on the townie.
     */
    boolean hasMoodEffect(UUID uuid, ResourceLocation effect);
}
