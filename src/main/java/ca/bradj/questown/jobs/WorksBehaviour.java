package ca.bradj.questown.jobs;

import ca.bradj.questown.integration.minecraft.MCHeldItem;
import ca.bradj.questown.integration.minecraft.MCTownItem;
import ca.bradj.questown.integration.minecraft.MCTownState;
import ca.bradj.questown.jobs.gatherer.GathererTools;
import ca.bradj.questown.jobs.production.ProductionStatus;
import ca.bradj.questown.town.Warper;
import ca.bradj.questown.town.interfaces.TownInterface;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.block.Block;
import org.apache.commons.lang3.function.TriFunction;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.UUID;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

public class WorksBehaviour {
    private static final ResourceLocation NOT_REQUIRED_BECAUSE_BLOCKLESS_JOB = null;
    private static final Predicate<Block> NOT_REQUIRED_BECUASE_HAS_NO_JOB_BLOCK = (block) -> false;
    private static final ItemStack NOT_REQUIRED_BECAUSE_NO_JOB_QUEST = ItemStack.EMPTY;

    public static Warper<MCTownState> productionWarper(
            JobID id,
            WarpInput warpInput,
            boolean prioritizeExtraction,
            int pauseForAction,
            int maxState,
            ImmutableMap<Integer, Ingredient> toolsRequiredAtStates,
            ImmutableMap<Integer, Integer> workRequiredAtStates,
            ImmutableMap<Integer, Ingredient> ingredientsRequiredAtStates,
            ImmutableMap<Integer, Integer> ingredientQtyRequiredAtStates,
            ImmutableMap<Integer, Integer> timeRequiredAtStates,
            Function<TownData, ItemStack> result
    ) {
        MCTownStateWorldInteraction wi = new MCTownStateWorldInteraction(
                id,
                warpInput.villagerIndex(),
                pauseForAction,
                maxState,
                toolsRequiredAtStates,
                workRequiredAtStates,
                ingredientsRequiredAtStates,
                ingredientQtyRequiredAtStates,
                timeRequiredAtStates,
                () -> MCHeldItem.fromMCItemStack(result.get())
        );
        return DeclarativeJobs.warper(wi, maxState, prioritizeExtraction);
    }


    public interface JobFunc extends BiFunction<TownInterface, UUID, Job<MCHeldItem, ? extends ImmutableSnapshot<MCHeldItem, ?>, ? extends IStatus<?>>> {

    }

    private interface SnapshotFunc extends TriFunction<JobID, String, ImmutableList<MCHeldItem>, ImmutableSnapshot<MCHeldItem, ?>> {

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
            JobFunc jobFunc,
            JobID jobId,
            Predicate<Block> isJobBlock,
            ResourceLocation baseRoom,
            Function<TownData, ImmutableSet<MCTownItem>> results,
            ItemStack initialRequest,
            int maxState,
            ImmutableMap<Integer, Ingredient> ingredients,
            ImmutableMap<Integer, Integer> ingredientQty,
            ImmutableMap<Integer, Ingredient> tools,
            ImmutableMap<Integer, Integer> work,
            ImmutableMap<Integer, Integer> time,
            boolean prioritizeExtraction,
            int actionDuration
    ) {
        return new Work(
                jobFunc,
                productionJobSnapshot(jobId),
                isJobBlock,
                baseRoom,
                ProductionStatus.FACTORY.idle(),
                results,
                initialRequest,
                status -> getProductionNeeds(ingredients, tools),
                warpInput -> WorksBehaviour.productionWarper(
                        jobId,
                        warpInput,
                        prioritizeExtraction,
                        actionDuration,
                        maxState,
                        tools,
                        work,
                        ingredients,
                        ingredientQty,
                        time,
                        new Supplier<ItemStack>() {
                            @Override
                            public ItemStack get() {
                                return RESULT;
                            }
                        }
                )
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
