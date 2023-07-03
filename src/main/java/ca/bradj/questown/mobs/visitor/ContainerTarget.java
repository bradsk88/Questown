package ca.bradj.questown.mobs.visitor;

import ca.bradj.questown.integration.minecraft.MCTownItem;
import com.google.common.collect.ImmutableSet;
import net.minecraft.core.BlockPos;
import net.minecraft.world.Container;
import net.minecraft.world.item.Item;

public class ContainerTarget {

    private final ValidCheck check;
    BlockPos position;
    Container container;

    public interface ValidCheck {
        boolean IsStillValid();
    }

    public ContainerTarget(
            BlockPos position,
            Container container,
            ValidCheck check
    ) {
        this.position = position;
        this.container = container;
        this.check = check;
    }

    public BlockPos getPosition() {
        return position;
    }

    public boolean hasAnyOf(ImmutableSet<Item> items) {
        return container.hasAnyOf(items);
    }

    public interface CheckFn {
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

    public boolean isStillValid() {
        return check.IsStillValid();
    }

    @Override
    public String toString() {
        return "FoodTarget{" +
                "position=" + position +
                ", container=" + container +
                '}';
    }
}
