package ca.bradj.questown.jobs.declarative;

import ca.bradj.questown.integration.minecraft.MCCoupledHeldItem;
import ca.bradj.questown.town.interfaces.WorkStateContainer;
import com.google.common.collect.ImmutableMap;
import net.minecraft.core.BlockPos;

import java.util.function.Function;

public class MCItemWI extends AbstractItemWI<BlockPos, MCExtra, MCCoupledHeldItem> {

    public MCItemWI(
            ImmutableMap<Integer, Function<MCCoupledHeldItem, Boolean>> ingredientsRequiredAtStates,
            ImmutableMap<Integer, Integer> ingredientQtyRequiredAtStates,
            ImmutableMap<Integer, Integer> workRequiredAtStates,
            ImmutableMap<Integer, Integer> timeRequiredAtStates,
            InventoryHandle<MCCoupledHeldItem> inventory
    ) {
        super(
                ingredientsRequiredAtStates,
                ingredientQtyRequiredAtStates,
                workRequiredAtStates,
                timeRequiredAtStates,
                inventory
        );
    }

    @Override
    protected boolean canInsertItem(
            MCExtra extra,
            MCCoupledHeldItem item,
            BlockPos bp
    ) {
        return extra.work().canInsertItem(item, bp);
    }

    @Override
    protected WorkStateContainer<BlockPos> getWorkStatuses(MCExtra extra) {
        return extra.work();
    }
}
