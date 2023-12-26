package ca.bradj.questown.jobs;

import ca.bradj.questown.integration.minecraft.MCHeldItem;
import ca.bradj.questown.integration.minecraft.MCTownItem;
import ca.bradj.questown.integration.minecraft.MCTownState;
import ca.bradj.questown.jobs.declarative.AbstractWorldInteraction;
import ca.bradj.questown.jobs.declarative.InventoryHandle;
import ca.bradj.questown.town.interfaces.WorkStateContainer;
import com.google.common.collect.ImmutableMap;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;

import java.util.Collection;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;

public class MCTownStateWorldInteraction extends AbstractWorldInteraction<MCTownState, BlockPos, MCTownItem, MCHeldItem> {
    private final InventoryHandle<MCHeldItem> inventory;

    public MCTownStateWorldInteraction(
            JobID jobId,
            int interval,
            int maxState,
            ImmutableMap<Integer, Ingredient> toolsRequiredAtStates,
            ImmutableMap<Integer, Integer> workRequiredAtStates,
            ImmutableMap<Integer, Ingredient> ingredientsRequiredAtStates,
            ImmutableMap<Integer, Integer> ingredientQuantityRequiredAtStates,
            ImmutableMap<Integer, Integer> timeRequiredAtStates, Supplier<Collection<MCTownItem>> journalItems,
            InventoryHandle<MCHeldItem> inventory
    ) {
        super(
                jobId, interval, maxState, toolsRequiredAtStates,
                workRequiredAtStates, ingredientsRequiredAtStates,
                ingredientQuantityRequiredAtStates, timeRequiredAtStates,
                journalItems, inventory
        );
        this.inventory = inventory;
    }

    @Override
    protected void degradeTool(MCTownState mcTownState, Function<MCTownItem, Boolean> isExpectedTool) {
        int i = 0;
        for (MCHeldItem item : this.inventory.getItems()) {
            i++;
            if (!isExpectedTool.apply(item.get())) {
                continue;
            }
            ItemStack itemStack = item.get().toItemStack();
            itemStack.getItem().damageItem(itemStack ,1, null, e -> {}); // TODO: Get a reference to the villager?
            inventory.set(i - 1, MCHeldItem.fromMCItemStack(itemStack));
        }
    }

    @Override
    protected boolean canInsertItem(MCTownState mcTownState, MCHeldItem item, BlockPos bp) {
        return mcTownState.can;
    }

    @Override
    protected WorkStateContainer<BlockPos> getWorkStatuses(MCTownState mcTownState) {
        return null;
    }

    @Override
    protected boolean tryExtractOre(MCTownState mcTownState, BlockPos position) {
        return false;
    }

    @Override
    protected boolean isEntityClose(MCTownState mcTownState, BlockPos position) {
        return false;
    }

    @Override
    protected boolean isReady(MCTownState mcTownState) {
        return false;
    }

    @Override
    public Map<Integer, Integer> ingredientQuantityRequiredAtStates() {
        return null;
    }
}
