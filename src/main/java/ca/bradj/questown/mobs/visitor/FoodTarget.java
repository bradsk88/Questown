package ca.bradj.questown.mobs.visitor;

import ca.bradj.questown.integration.minecraft.MCTownItem;
import com.google.common.collect.ImmutableSet;
import net.minecraft.core.BlockPos;
import net.minecraft.world.Container;
import net.minecraft.world.item.Item;

public class FoodTarget {

    BlockPos position;
    Container container;

    public FoodTarget(
            BlockPos position,
            Container container
    ) {
        this.position = position;
        this.container = container;
    }

    public BlockPos getPosition() {
        return position;
    }

    public boolean hasAnyOf(ImmutableSet<Item> items) {
        return container.hasAnyOf(items);
    }

    interface CheckFn {
        boolean Matches(MCTownItem item);
    }

    public boolean hasItem(CheckFn c) {
        for (int i = 0; i < container.getContainerSize(); i++) {
            if (c.Matches(new MCTownItem(container.getItem(i).getItem()))) {
                return true;
            }
        }
        return false;
    }

    @Override
    public String toString() {
        return "FoodTarget{" +
                "position=" + position +
                ", container=" + container +
                '}';
    }
}
