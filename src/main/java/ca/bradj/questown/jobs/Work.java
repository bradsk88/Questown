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
    final Function<ServerLevel, @Nullable Ingredient> initialRequest;
    final Function<List<MCHeldItem>, Collection<Ingredient>> needs;
    private final Function<WorksBehaviour.WarpInput, Warper<ServerLevel, MCTownState>> warper;
    final int priority;
    private final ImmutableList<String> specialGlobalRules;
    private Overrides overrides;
    private boolean hasNoOutput;
    private @Nullable SlotPrecondition slotPrecondition;
    private @Nullable String proficiencyId;

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

    public Work withProficiencyId(@Nullable String proficiencyId) {
        this.proficiencyId = proficiencyId;
        return this;
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
        return work;
    }

    public @Nullable ResourceLocation applyStatusTextureOverride(IStatus<?> status) {
        return overrides.statusTextures().get(status);
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

}
