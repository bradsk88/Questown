package ca.bradj.questown.jobs;

import ca.bradj.questown.core.Pair;
import ca.bradj.questown.integration.minecraft.MCHeldItem;
import ca.bradj.questown.integration.minecraft.MCTownItem;
import ca.bradj.questown.integration.minecraft.MCTownState;
import ca.bradj.questown.town.Warper;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.function.BiPredicate;
import java.util.function.Function;
import java.util.function.Predicate;

public class Work {

    public final JobID id;
    public final @Nullable JobID parentID;
    public final ItemStack icon;
    public final WorksBehaviour.JobFunc jobFunc;
    final WorksBehaviour.SnapshotFunc snapshotFunc;
    final Predicate<JobBlockTestContext> isJobBlock;
    final BiPredicate<WorkLocation.BlockInfo, BlockPos> shouldInitializeWorkState;
    public final ResourceLocation baseRoom;
    final IStatus<?> initialStatus;
    public final Function<WorksBehaviour.TownData, ImmutableSet<MCTownItem>> results;
    public final Function<ServerLevel, @Nullable Ingredient> initialRequest;
    final Function<List<MCHeldItem>, Collection<Ingredient>> needs;
    private final Function<WorksBehaviour.WarpInput, Warper<ServerLevel, MCTownState>> warper;
    final int priority;
    private final ImmutableList<String> specialGlobalRules;
    private Overrides overrides;
    private boolean hasNoOutput;
    private @Nullable SlotPrecondition slotPrecondition;
    private @Nullable String proficiencyId;
    /**
     * True when the job's JSON opts it into its root's initial unlock set via
     * `unlocked_with_root_job` — e.g. `cook/fuel`, so a freshly staffed cook
     * unlocks a working set (extract + fuel) instead of a single root job that
     * re-selects itself every cooldown. Recipes stay progression-gated;
     * see `.scratch/cook-skill-unlock/cook-skill-unlock.md`.
     */
    private boolean unlockedWithRootJob;

    /**
     * The townie level at which this job becomes available for skill-tab
     * unlocking (the JSON `available_at_level`, default {@link #DEFAULT_AVAILABLE_AT_LEVEL}).
     * Townies start at level 1, so the default keeps every ordinary job available the moment
     * the skills tab first appears (level 2); the material-tier crafter jobs declare higher
     * values (wooden 7, stone 12, iron 17, golden 22, diamond 27) so a profession's tool
     * progression follows the townie's level. The gate applies to jobs NOT yet unlocked only —
     * tutorial-granted jobs are already in the unlocked set and are never retroactively
     * hidden (2026-10-13 skills-tab ruling, skills-tab-hidden-until-level-2.md).
     */
    private int availableAtLevel = DEFAULT_AVAILABLE_AT_LEVEL;
    /** The JSON `experience`: XP per completion, replacing the formula. Null = the formula. */
    private @Nullable Integer experience;

    /** The `available_at_level` default: the first level at which the skills tab appears. */
    public static final int DEFAULT_AVAILABLE_AT_LEVEL = 2;

    public Work(
            JobID id,
            @Nullable JobID parentID,
            ItemStack icon,
            WorksBehaviour.JobFunc jobFunc,
            WorksBehaviour.SnapshotFunc snapshotFunc,
            Predicate<JobBlockTestContext> isJobBlock,
            BiPredicate<WorkLocation.BlockInfo, BlockPos> shouldInitializeWorkState,
            ResourceLocation baseRoom,
            IStatus<?> initialStatus,
            Function<WorksBehaviour.TownData, ImmutableSet<MCTownItem>> results,
            Function<ServerLevel, @Nullable Ingredient> initialRequest,
            Function<List<MCHeldItem>, Collection<Ingredient>> needs,
            Function<WorksBehaviour.WarpInput, Warper<ServerLevel, MCTownState>> warper,
            int priority,
            boolean hasNoOutput,
            ImmutableList<String> specialGlobalRules
    ) {
        this.id = id;
        this.parentID = parentID;
        this.icon = icon;
        this.jobFunc = jobFunc;
        this.snapshotFunc = snapshotFunc;
        this.isJobBlock = isJobBlock;
        this.shouldInitializeWorkState = shouldInitializeWorkState;
        this.baseRoom = baseRoom;
        this.initialStatus = initialStatus;
        this.results = results;
        this.initialRequest = initialRequest;
        this.needs = needs;
        this.warper = warper;
        this.priority = priority;
        this.specialGlobalRules = specialGlobalRules;
        this.overrides = Overrides.none();
        this.hasNoOutput = hasNoOutput;
    }

    public ImmutableList<String> getSpecialGlobalRules() {
        return specialGlobalRules;
    }

    /**
     * The job definition's proficiency-id (a free-form string; multiple jobs may
     * share one). Null means proficiency does not apply to this job — a flat 1×
     * work-speed multiplier and no leveling. See ADR-0010.
     */
    public @Nullable String getProficiencyId() {
        return proficiencyId;
    }

    public boolean isUnlockedWithRootJob() {
        return unlockedWithRootJob;
    }

    /**
     * Pure "unlocked with the root job at staff" decision: a job joins its
     * root's initial set when it is a progression root (no parent) or its JSON
     * opted in via `unlocked_with_root_job`. Kept as a plain (parent, flag)
     * static so it is unit-testable without a Forge registry — the
     * `ResourceJobLoader` parse and the `getDefaultWork` consumers are the
     * registry-bound half (compile + held-run coverage).
     */
    public static boolean isUnlockedInitially(
            @Nullable JobID parentID,
            boolean unlockedWithRootJob
    ) {
        return parentID == null || unlockedWithRootJob;
    }

    /**
     * The same-root fallback's pick: the first candidate unlocked with the root (see
     * {@link #isUnlockedInitially}), else the first candidate. Pure over accessors so it is
     * unit-testable without the Forge registry.
     */
    public static <T> Optional<T> preferInitiallyUnlocked(
            List<T> candidates,
            Function<T, @Nullable JobID> parentOf,
            Predicate<T> unlockedWithRootJobOf
    ) {
        Optional<T> initial = candidates.stream()
                .filter(c -> isUnlockedInitially(parentOf.apply(c), unlockedWithRootJobOf.test(c)))
                .findFirst();
        if (initial.isPresent()) {
            return initial;
        }
        return candidates.stream().findFirst();
    }

    public Work withProficiencyId(@Nullable String proficiencyId) {
        this.proficiencyId = proficiencyId;
        return this;
    }

    /** Chains the `unlocked_with_root_job` JSON flag parsed by {@code ResourceJobLoader}. */
    public Work withUnlockedWithRootJob(boolean unlockedWithRootJob) {
        this.unlockedWithRootJob = unlockedWithRootJob;
        return this;
    }

    public int getAvailableAtLevel() {
        return availableAtLevel;
    }

    /** Chains the `available_at_level` JSON value parsed by {@code ResourceJobLoader}. */
    public Work withAvailableAtLevel(int availableAtLevel) {
        this.availableAtLevel = availableAtLevel;
        return this;
    }

    /** Chains the optional `experience` JSON value parsed by {@code ResourceJobLoader}. */
    public Work withExperience(@Nullable Integer experience) {
        this.experience = experience;
        return this;
    }

    /**
     * XP for one completion: the job's declared `experience`, else {@code formula}
     * ({@code DeclarativeJob.getExperienceEarned}). The cook and baker jobs earned 1 from the
     * formula (dev ruling 2026-10-24, {@code JobExperienceFieldTest}).
     */
    public int experienceEarned(int formula) {
        if (experience == null) {
            return formula;
        }
        return experience;
    }

    public Work withPriority(int priority) {
        Work work = new Work(
                id,
                parentID,
                icon,
                jobFunc,
                snapshotFunc,
                isJobBlock,
                shouldInitializeWorkState,
                baseRoom,
                initialStatus,
                results,
                initialRequest,
                needs,
                warper,
                priority,
                hasNoOutput,
                specialGlobalRules
        );
        work.proficiencyId = proficiencyId;
        work.unlockedWithRootJob = unlockedWithRootJob;
        work.experience = experience;
        return work;
    }

    public Work withNeeds(
            Function<List<MCHeldItem>, Collection<Ingredient>> needz
    ) {
        Work work = new Work(
                id,
                parentID,
                icon,
                jobFunc,
                snapshotFunc,
                isJobBlock,
                shouldInitializeWorkState,
                baseRoom,
                initialStatus,
                results,
                initialRequest,
                needz,
                warper,
                priority,
                hasNoOutput,
                specialGlobalRules
        );
        work.proficiencyId = proficiencyId;
        work.unlockedWithRootJob = unlockedWithRootJob;
        work.experience = experience;
        return work;
    }

    public @Nullable ResourceLocation applyStatusTextureOverride(IStatus<?> status) {
        return overrides.textureFor(status);
    }

    public @Nullable Pair<String, String> applyStatusTextOverride(IStatus<?> status) {
        return overrides.statusTextOverrides().get(status);
    }

    public Work withOverrides(
            @NotNull Overrides overrides
    ) {
        Work work = new Work(
                id,
                parentID,
                icon,
                jobFunc,
                snapshotFunc,
                isJobBlock,
                shouldInitializeWorkState,
                baseRoom,
                initialStatus,
                results,
                initialRequest,
                needs,
                warper,
                priority,
                hasNoOutput,
                specialGlobalRules
        );
        work.overrides = overrides;
        work.proficiencyId = proficiencyId;
        work.unlockedWithRootJob = unlockedWithRootJob;
        work.experience = experience;
        return work;
    }

    public Work withSlotPrecondition(@NotNull SlotPrecondition sp) {
        Work work = new Work(
                id, parentID, icon, jobFunc, snapshotFunc, isJobBlock,
                shouldInitializeWorkState, baseRoom, initialStatus, results,
                initialRequest, needs, warper, priority, hasNoOutput, specialGlobalRules
        );
        work.overrides = this.overrides;
        work.slotPrecondition = sp;
        work.proficiencyId = proficiencyId;
        work.unlockedWithRootJob = unlockedWithRootJob;
        work.experience = experience;
        return work;
    }

    public @Nullable SlotPrecondition getSlotPrecondition() {
        return slotPrecondition;
    }

    public boolean hasNoOutput() {
        return hasNoOutput;
    }

    public Warper<ServerLevel, MCTownState> warper(WorksBehaviour.WarpInput input) {
        return warper.apply(input.withSlotPrecondition(slotPrecondition));
    }

    /**
     * Whether this job's own block occupies the given position — the warp-lane work-block
     * assignment seam ({@code PostDowntimeWarper} assigns the job's real container block this
     * way instead of falling back to a virtual position). Tests the block-identity check
     * ({@code shouldInitializeWorkState}: block id + block-state properties), NOT the full
     * {@code isJobBlock} predicate, which for slot-gated jobs also demands a particular slot
     * content. The content check is wrong for assignment: it tests the world AS IT IS, while
     * the warp is still to produce it — a {@code baker/extract} that requires bread to already
     * sit in slot 2 could never be assigned on an oven the warp has not baked yet, so its
     * takes would drop at a virtual position and the warp's output would never be extracted.
     */
    public boolean isJobBlockAt(WorkLocation.BlockInfo blockInfo, BlockPos pos) {
        return shouldInitializeWorkState.test(blockInfo, pos);
    }

}
