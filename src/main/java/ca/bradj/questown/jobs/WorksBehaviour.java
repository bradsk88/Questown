package ca.bradj.questown.jobs;

import ca.bradj.questown.core.Config;
import ca.bradj.questown.core.VillagerUUID;
import ca.bradj.questown.integration.minecraft.MCHeldItem;
import ca.bradj.questown.integration.minecraft.MCTownItem;
import ca.bradj.questown.integration.minecraft.MCTownState;
import ca.bradj.questown.jobs.declarative.*;
import ca.bradj.questown.jobs.declarative.nomc.WorkSeekerJob;
import ca.bradj.questown.jobs.fetcher.FetcherHack;
import ca.bradj.questown.jobs.gatherer.GathererTools;
import ca.bradj.questown.jobs.production.ProductionStatus;
import ca.bradj.questown.town.Claim;
import ca.bradj.questown.town.Warper;
import ca.bradj.roomrecipes.adapter.RoomRecipeMatch;
import ca.bradj.roomrecipes.serialization.MCRoom;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import org.apache.commons.lang3.function.TriFunction;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.BiFunction;
import java.util.function.Function;

public class WorksBehaviour {

    public static Warper<ServerLevel, MCTownState> productionWarper(
            BlockPos townPos,
            JobID id,
            WarpInput warpInput,
            boolean prioritizeExtraction,
            Function<TimeWarpWorldInteraction.Inputs, Claim> claimSpots,
            int pauseForAction,
            WorkStates states,
            BiFunction<ServerLevel, Collection<MCHeldItem>, Iterable<MCHeldItem>> resultGenerator,
            Map<ProductionStatus, Collection<String>> specialRules
    ) {
        TimeWarpWorldInteraction wi = new TimeWarpWorldInteraction(
                townPos,
                id,
                warpInput.villagerIndex(),
                pauseForAction,
                states.maxState(),
                fromStates(states),
                resultGenerator,
                claimSpots,
                specialRules,
                warpInput.roomPositions()
        );
        return DeclarativeJobs.warper(wi, states.maxState(), prioritizeExtraction);
    }

    private static DeclarativeJobChecks<TimeWarpWorldInteraction.Inputs, MCHeldItem, MCTownItem, RoomRecipeMatch<MCRoom>, BlockPos> fromStates(WorkStates states) {
        return new DeclarativeJobChecks<>(
                Jobs.unMCHeld3(states.ingredientsRequired()),
                states.ingredientQtyRequired(),
                Jobs.unMC5(states.toolsRequired()),
                states.workRequired(),
                states.timeRequired(),
                (r) -> true,
                (b) -> true // TODO: Should only return true if job block exists in town
        );
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
            Function<ServerLevel, Ingredient> result
    ) {
        return (t) -> {
            Ingredient i = result.apply(t.serverLevel());
            return i == null ? ImmutableSet.of() : MCTownItem.fromIngredient(i);
        };
    }

    public static WorkDescription standardDescription(Function<ServerLevel, @Nullable Ingredient> result) {
        return new WorkDescription(
                WorksBehaviour.standardProductionResult(result),
                result
        );
    }

    public static WorkDescription noResultDescription() {
        return new WorkDescription(
                (td) -> ImmutableSet.of(MCTownItem.Air()),
                null
        );
    }

    public interface JobFunc {
        Job<MCHeldItem, ? extends ImmutableSnapshot<MCHeldItem, ?>, ? extends IStatus<?>> apply(UUID owner);

        Job<MCHeldItem, ? extends ImmutableSnapshot<MCHeldItem, ?>, ? extends IStatus<?>> get(VillagerUUID owner);
    }

    public interface SnapshotFunc extends
            TriFunction<JobID, String, ImmutableList<MCHeldItem>, ImmutableSnapshot<MCHeldItem, ?>> {

    }

    public record TownData(
            ServerLevel serverLevel,
            Function<GathererTools.LootTablePrefix, ImmutableSet<MCTownItem>> allKnownGatherItemsFn
    ) {
    }


    public record WarpInput(
            int villagerIndex,
            BlockPos townFlagPos,
            Collection<BlockPos> roomPositions
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
            // Don't add more optional args here. Use chaining like "withNeeds".
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
                getJobFunc(jobId, location, states, world, special, workSound, expiration),
                productionJobSnapshot(jobId),
                location.isJobBlock(),
                location.shouldInitializeWorkState(),
                location.baseRoom(),
                ProductionStatus.FACTORY.idle(),
                description.currentlyPossibleResults(),
                description.initialRequest(),
                (items) -> getProductionNeeds(jobId, states, items),
                warpInput -> WorksBehaviour.productionWarper(
                        warpInput.townFlagPos(),
                        jobId,
                        warpInput,
                        special.containsGlobal(SpecialRules.PRIORITIZE_EXTRACTION),
                        inputs -> {
                            if (!special.containsGlobal(SpecialRules.CLAIM_SPOT)) {
                                return null;
                            }
                            return new Claim(inputs.vUUID(), Config.BLOCK_CLAIMS_TICK_LIMIT.get());
                        },
                        world.actionDuration(),
                        states,
                        world.resultGenerator()::generate,
                        special.specialStatusRules()
                ),
                1,
                world.resultGenerator().isResultAlwaysEmpty(),
                special.specialGlobalRules()
        );
    }

    private static @NotNull JobFunc getJobFunc(
            JobID jobId,
            WorkLocation location,
            WorkStates states,
            WorkWorldInteractions world,
            WorkSpecialRules special,
            @Nullable SoundInfo workSound,
            ExpirationRules expiration
    ) {

        return new JobFunc() {
            @Override
            public Job<MCHeldItem, ? extends ImmutableSnapshot<MCHeldItem, ?>, ? extends IStatus<?>> apply(UUID owner) {
                // TODO: Add support for different inventory sizes
                return new DeclarativeJob(
                        owner, 6,
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
                        world.resultGenerator()::generate,
                        workSound
                );
            }

            @Override
            public Job<MCHeldItem, ? extends ImmutableSnapshot<MCHeldItem, ?>, ? extends IStatus<?>> get(VillagerUUID owner) {
                return new DeclarativeJob(
                        VillagerUUID.get(owner), 6,
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
                        world.resultGenerator()::generate,
                        workSound
                );
            }
        };
    }

    private static @NotNull List<Ingredient> getProductionNeeds(
            JobID jobId,
            WorkStates states,
            List<MCHeldItem> heldItems
    ) {
        if (FetcherHack.isFetcher(jobId)) {
            return FetcherHack.getProductionNeeds(heldItems);
        }
        return getProductionNeeds(states.ingredientsRequired(), states.toolsRequired());
    }

    @NotNull
    public static ExpirationRules productionExpiration() {
        return new ExpirationRules(
                // "Giving up" is important for town data. For example, it helps
                // us calculate which rooms are needed but not found. These
                // rules will just fall back to original job, so it will
                // effectively keep doing this job forever - even though it
                // does technically "expire".
                Config.MAX_INITIAL_TICKS_WITHOUT_SUPPLIES,
                Config.MAX_TICKS_WITHOUT_SUPPLIES,
                WorkSeekerJob::getIDForRoot,
                // FIXME: This should be 3000 to make sure crafters don't get stuck
                //  Can we add this to the JSON definition spec?
                () -> 6000L,
                jobId -> jobId
        );
    }

    @NotNull
    public static SnapshotFunc productionJobSnapshot(JobID id) {
        return (jobId, status, items) -> new SimpleSnapshot<>(
                id,
                ProductionStatus.fromNumber(status),
                items
        );
    }

    @NotNull
    public static List<Ingredient> getProductionNeeds(
            ImmutableMap<Integer, Ingredient> ing,
            ImmutableMap<Integer, Ingredient> tools
    ) {
        ImmutableList.Builder<Ingredient> b = ImmutableList.builder();
        ing.values().forEach(b::add);
        tools.values().forEach(b::add);
        return b.build();
    }
}
