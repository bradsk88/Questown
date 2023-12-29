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
import java.util.Stack;
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
            itemStack.getItem().damageItem(itemStack ,1, null, e -> {}); // TODO: Get a reference to the villager?
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
                Function<MCHeldItem, Boolean> ings = ingredientsRequiredAtStates().get(workStates.processingState());
                if (ings != null) {
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
                Function<MCHeldItem, Boolean> ings = ingredientsRequiredAtStates().get(workStates.processingState());
                if (ings != null) {
                    for (ContainerTarget<MCContainer, MCTownItem> container : containers) {
                        if (container.hasItem(i -> ings.apply(MCHeldItem.fromTown(i)))) {
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
        };
    }

    public EntityInvStateProvider<Integer> asInventory(MCTownState outState, int state) {
        return new EntityInvStateProvider<Integer>() {
            @Override
            public boolean inventoryFull() {
                Collection<MCHeldItem> items = getHeldItems(outState, villagerIndex);
                return items.stream().noneMatch(MCHeldItem::isEmpty);
            }

            @Override
            public boolean hasNonSupplyItems() {
                Collection<MCHeldItem> items = getHeldItems(outState, villagerIndex);
                Function<MCHeldItem, Boolean> ings = ingredientsRequiredAtStates().get(state);
                if (ings == null && !items.isEmpty()) {
                    return true;
                }
                return items.stream()
                        .filter(Predicates.not(Item::isEmpty))
                        .anyMatch(v -> !ings.apply(v));
            }

            @Override
            public Map<Integer, Boolean> getSupplyItemStatus() {
                Collection<MCHeldItem> items = getHeldItems(outState, villagerIndex);
                Function<MCHeldItem, Boolean> ings = ingredientsRequiredAtStates().get(state);
                if (ings == null) {
                    return ImmutableMap.of();
                }
                return ImmutableMap.of(
                        state, items.stream().anyMatch(ings::apply)
                );
            }
        };
    }

    public MCTownState simulateDropLoot(MCTownState outState, ProductionStatus status) {
        Collection<MCHeldItem> items = getHeldItems(outState, villagerIndex);
        ProductionTimeWarper.Result<MCHeldItem> r = new ProductionTimeWarper.Result<>(status, ImmutableList.copyOf(items));
        ProductionTimeWarper.simulateDropLoot(r, itemz -> {
            Stack<MCHeldItem> stack = new Stack<>();
            stack.addAll(itemz);
            while (!stack.isEmpty()) {
                for (ContainerTarget<MCContainer, MCTownItem> container : outState.containers) {
                    if (container.isFull()) {
                        continue;
                    }
                    for (int i = 0; i < container.size(); i++) {
                        if (container.getItem(i).isEmpty()) {
                                container.setItem(i, stack.pop().toItem());
                        }
                    }
                }
            }
            return ImmutableList.copyOf(stack);
        }, MCHeldItem::Air);
        return outState;
    }
}
