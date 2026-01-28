package ca.bradj.questown.jobs;

import ca.bradj.questown.core.Pair;
import ca.bradj.questown.integration.minecraft.MCHeldItem;
import ca.bradj.questown.integration.minecraft.MCTownItem;
import ca.bradj.questown.integration.minecraft.MCTownState;
import ca.bradj.questown.town.Warper;
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
    private Overrides overrides;
    private boolean hasNoOutput;

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
            boolean hasNoOutput
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
        this.overrides = Overrides.none();
        this.hasNoOutput = hasNoOutput;
    }

    public Work withPriority(int priority) {
        return new Work(
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
                hasNoOutput
        );
    }

    public Work withNeeds(Function<List<MCHeldItem>, Collection<Ingredient>> needz) {
        return new Work(
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
                hasNoOutput
        );
    }

    public @Nullable ResourceLocation applyStatusTextureOverride(IStatus<?> status) {
        return overrides.statusTextures().get(status);
    }

    public @Nullable Pair<String, String> applyStatusTextOverride(IStatus<?> status) {
        return overrides.statusTextOverrides().get(status);
    }

    public Work withOverrides(@NotNull Overrides overrides) {
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
                hasNoOutput
        );
        work.overrides = overrides;
        return work;
    }

    public boolean hasNoOutput() {
        return hasNoOutput;
    }

    public Warper<ServerLevel, MCTownState> warper(WorksBehaviour.WarpInput input) {
        return warper.apply(input);
    }

}
