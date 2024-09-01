package ca.bradj.questown.jobs;

import ca.bradj.questown.core.Config;
import ca.bradj.questown.integration.minecraft.MCHeldItem;
import ca.bradj.questown.integration.minecraft.MCTownItem;
import ca.bradj.questown.integration.minecraft.MCTownState;
import ca.bradj.questown.jobs.declarative.SoundInfo;
import ca.bradj.questown.jobs.declarative.nomc.WorkSeekerJob;
import ca.bradj.questown.jobs.gatherer.GathererTools;
import ca.bradj.questown.jobs.production.ProductionStatus;
import ca.bradj.questown.town.Claim;
import ca.bradj.questown.town.Warper;
import ca.bradj.questown.town.interfaces.TownInterface;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.apache.commons.lang3.function.TriFunction;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.*;

public class WorksBehaviour {
    private static final ResourceLocation NOT_REQUIRED_BECAUSE_BLOCKLESS_JOB = null;
    static final BiPredicate NOT_REQUIRED_BECUASE_HAS_NO_JOB_BLOCK = (sl, bp) -> false;
    private static final ItemStack NOT_REQUIRED_BECAUSE_NO_JOB_QUEST = ItemStack.EMPTY;

    public static Warper<ServerLevel, MCTownState> productionWarper(
            JobID id,
            WarpInput warpInput,
            boolean prioritizeExtraction,
            Function<MCTownStateWorldInteraction.Inputs, Claim> claimSpots,
            int pauseForAction,
            WorkStates states,
            BiFunction<ServerLevel, Collection<MCHeldItem>, Iterable<MCHeldItem>> resultGenerator,
            Map<ProductionStatus, Collection<String>> specialRules
    ) {
        MCTownStateWorldInteraction wi = new MCTownStateWorldInteraction(
                id,
                warpInput.villagerIndex(),
                pauseForAction,
                states,
                resultGenerator,
                claimSpots,
                specialRules
        );
        return DeclarativeJobs.warper(wi, states.maxState(), prioritizeExtraction);
    }

    public static BiFunction<ServerLevel, Collection<MCHeldItem>, Iterable<MCHeldItem>> singleItemOutput(
            Supplier<ItemStack> result
    ) {
        return (s, j) -> ImmutableSet.of(MCHeldItem.fromMCItemStack(result.get()));
    }

    public static BiFunction<ServerLevel, Collection<MCHeldItem>, Iterable<MCHeldItem>> noOutput() {
        return (s, j) -> ImmutableSet.of();
    }

    public static WorkSpecialRules standardProductionRules() {
        return new WorkSpecialRules(
                ImmutableMap.of(), // No stage rules
                ImmutableList.of(
                        SpecialRules.PRIORITIZE_EXTRACTION,
                        SpecialRules.SHARED_WORK_STATUS
                )
        );
    }

    public static Function<TownData, ImmutableSet<MCTownItem>> standardProductionResult(
            Supplier<ItemStack> result
    ) {
        return (t) -> {
            ItemStack i = result.get();
            return i == null ? ImmutableSet.of() : ImmutableSet.of(MCTownItem.fromMCItemStack(i));
        };
    }

    public static WorkDescription standardDescription(Supplier<@Nullable ItemStack> result) {
        return new WorkDescription(
                WorksBehaviour.standardProductionResult(result),
                result.get()
        );
    }

    public static WorkDescription noResultDescription() {
        return new WorkDescription(
                (td) -> ImmutableSet.of(MCTownItem.Air()),
                null
        );
    }

    public static WorkWorldInteractions standardWorldInteractions(
            int pauseForAction,
            Supplier<ItemStack> result
    ) {
        return new WorkWorldInteractions(
            pauseForAction,
            singleItemOutput(result.get()::copy)
        );
    }


    public interface JobFunc extends Function<UUID, Job<MCHeldItem, ? extends ImmutableSnapshot<MCHeldItem, ?>, ? extends IStatus<?>>> {

    }

    public interface SnapshotFunc extends TriFunction<JobID, String, ImmutableList<MCHeldItem>, ImmutableSnapshot<MCHeldItem, ?>> {

    }

    public interface BlockCheckFunc extends Function<Block, Boolean> {

    }

    public record TownData(
            Function<GathererTools.LootTablePrefix, ImmutableSet<MCTownItem>> allKnownGatherItemsFn
    ) {
    }


    public record WarpInput(
            int villagerIndex
    ) {
    }

    public static Work productionWork(
            ItemStack icon,
            JobID jobId,
            JobID parentId,
            WorkDescription description,
            WorkLocation location,
            WorkStates state,
            WorkWorldInteractions world,
            WorkSpecialRules special,
            @Nullable SoundInfo workSound
    ) {
        return productionWork(
                parentId,
                icon,
                jobId,
                description,
                location,
                state,
                world,
                special,
                workSound,
                productionExpiration()
        );
    }

    public static Work productionWork(
            JobID parentID,
            ItemStack icon,
            JobID jobId,
            WorkDescription description,
            WorkLocation location,
            WorkStates states,
            WorkWorldInteractions world,
            WorkSpecialRules special,
            @Nullable SoundInfo workSound,
            ExpirationRules expiration
            ) {
        return new Work(
                jobId,
                parentID,
                icon,
                (UUID uuid) -> new DeclarativeJob(
                        uuid, 6, // TODO: Add support for different inventory sizes
                        jobId, location, states.maxState(),
                        world.actionDuration(),
                        states.ingredientsRequired(),
                        states.ingredientQtyRequired(),
                        states.toolsRequired(),
                        states.workRequired(),
                        states.timeRequired(),
                        special.specialStatusRules(),
                        special.specialGlobalRules(),
                        expiration,
                        world.resultGenerator(),
                        workSound
                ),
                productionJobSnapshot(jobId),
                location.isJobBlock(),
                location.baseRoom(),
                ProductionStatus.FACTORY.idle(),
                description.currentlyPossibleResults(),
                description.initialRequest(),
                status -> getProductionNeeds(states.ingredientsRequired(), states.toolsRequired()),
                warpInput -> WorksBehaviour.productionWarper(
                        jobId,
                        warpInput,
                        special.containsGlobal(SpecialRules.PRIORITIZE_EXTRACTION),
                        inputs -> {
                            if (!special.containsGlobal(SpecialRules.CLAIM_SPOT)) {
                                return null;
                            }
                            return new Claim(inputs.uuid(), Config.BLOCK_CLAIMS_TICK_LIMIT.get());
                        },
                        world.actionDuration(),
                        states,
                        world.resultGenerator(),
                        special.specialStatusRules()
                ),
                1
        );
    }

    @NotNull
    public static ExpirationRules productionExpiration() {
        return new ExpirationRules(
                Config.MAX_INITIAL_TICKS_WITHOUT_SUPPLIES::get,
                Config.MAX_TICKS_WITHOUT_SUPPLIES::get,
                WorkSeekerJob::getIDForRoot,
                () -> Long.MAX_VALUE,
                jobId -> jobId
        );
    }

    @NotNull
    public static SnapshotFunc productionJobSnapshot(JobID id) {
        return (jobId, status, items) -> new SimpleSnapshot<>(
                id,
                ProductionStatus.from(status),
                items
        );
    }

    @NotNull
    public static List<Ingredient> getProductionNeeds(
            ImmutableMap<Integer, Ingredient> ing,
            ImmutableMap<Integer, Ingredient> tools
    ) {
        // TODO: Is it okay that we ignore status here?
        ImmutableList.Builder<Ingredient> b = ImmutableList.builder();
        ing.values().forEach(b::add);
        tools.values().forEach(b::add);
        return b.build();
    }
}
