package ca.bradj.questown.jobs;

import ca.bradj.questown.integration.minecraft.MCContainer;
import ca.bradj.questown.integration.minecraft.MCHeldItem;
import ca.bradj.questown.integration.minecraft.MCTownItem;
import ca.bradj.questown.integration.minecraft.MCTownState;
import ca.bradj.questown.jobs.declarative.AbstractWorldInteraction;
import ca.bradj.questown.jobs.leaver.ContainerTarget;
import ca.bradj.questown.jobs.production.ProductionStatus;
import ca.bradj.questown.town.AbstractWorkStatusStore;
import ca.bradj.questown.town.TownState;
import ca.bradj.questown.town.interfaces.ImmutableWorkStateContainer;
import ca.bradj.roomrecipes.serialization.MCRoom;
import com.google.common.base.Predicates;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;

public class MCTownStateWorldInteraction extends AbstractWorldInteraction<MCTownState, BlockPos, MCTownItem, MCHeldItem, MCTownState> {
    private final BlockPos position;
    private final Supplier<MCHeldItem> result;
    private Map<Integer, Integer> ingredientQuantityRequiredAtStates;

    public MCTownStateWorldInteraction(
            JobID jobId,
            int villagerIndex,
            int interval,
            int maxState,
            ImmutableMap<Integer, Ingredient> toolsRequiredAtStates,
            ImmutableMap<Integer, Integer> workRequiredAtStates,
            ImmutableMap<Integer, Ingredient> ingredientsRequiredAtStates,
            ImmutableMap<Integer, Integer> ingredientQuantityRequiredAtStates,
            ImmutableMap<Integer, Integer> timeRequiredAtStates,
            Supplier<MCHeldItem> result
    ) {
        super(
                jobId, villagerIndex, interval, maxState, Jobs.unMC(toolsRequiredAtStates),
                workRequiredAtStates, Jobs.unMCHeld(ingredientsRequiredAtStates),
                ingredientQuantityRequiredAtStates, timeRequiredAtStates
        );
        this.position = new BlockPos(villagerIndex, villagerIndex, villagerIndex);
        this.result = result;
        this.ingredientQuantityRequiredAtStates = ingredientQuantityRequiredAtStates;
    }

    @Override
    protected MCTownState setHeldItem(MCTownState uxtra, MCTownState tuwn, int villagerIndex, int itemIndex, MCHeldItem item) {
        if (tuwn == null) {
            tuwn = uxtra;
        }
        TownState.VillagerData<MCHeldItem> villager = tuwn.villagers.get(villagerIndex);
        return tuwn.withVillagerData(villagerIndex, villager.withSetItem(itemIndex, item));
    }

    @Override
    protected MCTownState degradeTool(MCTownState mcTownState, @Nullable MCTownState tuwn, Function<MCTownItem, Boolean> isExpectedTool) {
        if (tuwn == null) {
            tuwn = mcTownState;
        }

        int i = 0;
        for (MCHeldItem item : this.getHeldItems(mcTownState, villagerIndex)) {
            i++;
            if (!isExpectedTool.apply(item.get())) {
                continue;
            }
            ItemStack itemStack = item.get().toItemStack();
            itemStack.getItem().damageItem(itemStack, 1, null, e -> {
            }); // TODO: Get a reference to the villager?
            return setHeldItem(mcTownState, tuwn, villagerIndex, i, MCHeldItem.fromMCItemStack(itemStack));
        }
        return tuwn;
    }

    @Override
    protected boolean canInsertItem(MCTownState mcTownState, MCHeldItem item, BlockPos bp) {
        return true;
    }

    @Override
    protected ImmutableWorkStateContainer<BlockPos, MCTownState> getWorkStatuses(MCTownState mcTownState) {
        return mcTownState;
    }

    @Override
    protected Collection<MCHeldItem> getHeldItems(MCTownState mcTownState, int villagerIndex) {
        TownState.VillagerData<MCHeldItem> vil = mcTownState.villagers.get(villagerIndex);
        return vil.journal.items();
    }

    @Override
    protected MCTownState tryExtractOre(MCTownState mcTownState, BlockPos position) {
        AbstractWorkStatusStore.State s = getJobBlockState(mcTownState, position);
        if (s != null && s.processingState() == maxState) {
            int i = 0;
            for (MCHeldItem item : getHeldItems(mcTownState, villagerIndex)) {
                i++;
                if (item.isEmpty()) {
                    mcTownState = setHeldItem(mcTownState, mcTownState, villagerIndex, i, result.get());
                    return mcTownState.setJobBlockState(position, AbstractWorkStatusStore.State.fresh());
                }
            }
        }
        return null;
    }

    @Override
    protected boolean isEntityClose(MCTownState mcTownState, BlockPos position) {
        return true;
    }

    @Override
    protected boolean isReady(MCTownState mcTownState) {
        return true;
    }

    @Override
    public Map<Integer, Integer> ingredientQuantityRequiredAtStates() {
        return ingredientQuantityRequiredAtStates;
    }

    public void injectTicks(int interval) {
        ticksSinceLastAction += interval;
    }

    public JobTownProvider<MCRoom> asTownJobs(
            @NotNull AbstractWorkStatusStore.State workStates,
            MCRoom mcRoom,
            @NotNull ImmutableList<ContainerTarget<MCContainer, MCTownItem>> containers
    ) {
        return new JobTownProvider<MCRoom>() {
            @Override
            public Collection<MCRoom> roomsWithCompletedProduct() {
                if (workStates.processingState() == maxState) {
                    return ImmutableList.of(mcRoom);
                }
                return ImmutableList.of();
            }

            @Override
            public Map<Integer, Collection<MCRoom>> roomsNeedingIngredientsByState() {
                int curState = workStates.processingState();
                Function<MCHeldItem, Boolean> ings = ingredientsRequiredAtStates().get(curState);
                if (ings != null) {
                    return ImmutableMap.of(workStates.processingState(), ImmutableList.of(mcRoom));
                }

                Function<MCTownItem, Boolean> toolChk = toolsRequiredAtStates.get(curState);
                if (toolChk == null) {
                    return ImmutableMap.of();
                }
                for (ContainerTarget<MCContainer, MCTownItem> container : containers) {
                    if (!container.hasItem(toolChk::apply)) {
                        continue;
                    }
                    return ImmutableMap.of(workStates.processingState(), ImmutableList.of(mcRoom));
                }
                return ImmutableMap.of();
            }

            @Override
            public boolean isUnfinishedTimeWorkPresent() {
                // TODO[ASAP]: Implement time work
                return false;
            }

            @Override
            public boolean hasSupplies() {
                // TODO: Reduce deuplication with DeclarativeJob.roomsNeedingIngredientsOrTools
                int curState = workStates.processingState();
                Function<MCHeldItem, Boolean> ings = ingredientsRequiredAtStates().get(curState);
                if (ings != null) {
                    for (ContainerTarget<MCContainer, MCTownItem> container : containers) {
                        if (container.hasItem(i -> ings.apply(MCHeldItem.fromTown(i)))) {
                            return true;
                        }
                    }
                }
                Function<MCTownItem, Boolean> toolChk = toolsRequiredAtStates.get(curState);
                if (toolChk != null) {
                    for (ContainerTarget<MCContainer, MCTownItem> container : containers) {
                        if (container.hasItem(toolChk::apply)) {
                            return true;
                        }
                    }
                }
                return false;
            }

            @Override
            public boolean hasSpace() {
                return containers.stream().anyMatch(v -> !v.isFull());
            }
        }

                ;
    }

    public EntityInvStateProvider<Integer> asInventory(Supplier<Collection<MCHeldItem>> heldItems, Supplier<Integer> state) {
        return new EntityInvStateProvider<Integer>() {
            @Override
            public boolean inventoryFull() {
                Collection<MCHeldItem> items = heldItems.get();
                return items.stream().noneMatch(MCHeldItem::isEmpty);
            }

            @Override
            public boolean hasNonSupplyItems() {
                return JobsClean.hasNonSupplyItems(
                        heldItems.get(),
                        state.get(),
                        Jobs.unFn(ingredientsRequiredAtStates()),
                        Jobs.unHeld(toolsRequiredAtStates)
                );
            }

            @Override
            public Map<Integer, Boolean> getSupplyItemStatus() {
                return JobsClean.getSupplyItemStatuses(
                        heldItems,
                        Jobs.unFn(ingredientsRequiredAtStates()),
                        Jobs.unHeld(toolsRequiredAtStates)
                );
            }
        };
    }

    public MCTownState simulateDropLoot(MCTownState outState, ProductionStatus status) {
        Collection<MCHeldItem> items = getHeldItems(outState, villagerIndex);
        ProductionTimeWarper.Result<MCHeldItem> r = new ProductionTimeWarper.Result<>(status, ImmutableList.copyOf(items));
        Function<ImmutableList<MCHeldItem>, Collection<MCHeldItem>> dropFn = itemz -> ProductionTimeWarper.dropIntoContainers(itemz, outState.containers);
        r = ProductionTimeWarper.simulateDropLoot(r, dropFn, MCHeldItem::Air);
        return outState.withVillagerData(villagerIndex, outState.villagers.get(villagerIndex).withItems(r.items()));
    }

    public @Nullable MCTownState simulateCollectSupplies(
            MCTownState inState, int processingState
    ) {
        Function<MCHeldItem, Boolean> ingr = ingredientsRequiredAtStates().get(processingState);

        if (ingr == null) {
            Function<MCTownItem, Boolean> toolchk = toolsRequiredAtStates.get(processingState);
            if (toolchk == null) {
                throw new IllegalStateException("No ingredients or tools required at state " + processingState + ". We shouldn't be collecting.");
            }
            ingr = (h) -> toolchk.apply(h.get());
        }

        final Function<MCHeldItem, Boolean> fingr = ingr;

        @Nullable Map.Entry<MCTownState, MCTownItem> removeResult = inState.withContainerItemRemoved(i -> fingr.apply(MCHeldItem.fromTown(i)));
        if (removeResult == null) {
            return null; // Item does not exist - collection failed
        }

        MCTownState outState = removeResult.getKey();

        TownState.VillagerData<MCHeldItem> villager = outState.villagers.get(villagerIndex);
        villager = villager.withAddedItem(MCHeldItem.fromTown(removeResult.getValue()));
        if (villager == null) {
            return null; // No space in inventory - collection failed
        }

        return outState.withVillagerData(villagerIndex, villager);
    }
}
