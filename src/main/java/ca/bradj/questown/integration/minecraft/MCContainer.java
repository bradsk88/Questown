package ca.bradj.questown.integration.minecraft;

import ca.bradj.questown.mobs.visitor.ContainerTarget;
import com.google.common.collect.ImmutableSet;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

public class MCContainer implements ContainerTarget.Container<MCTownItem> {

    private final Container container;

    public MCContainer(Container container) {
        this.container = container;
    }

    @Override
    public int size() {
        return container.getContainerSize();
    }

    @Override
    public MCTownItem getItem(int i) {
        return new MCTownItem(container.getItem(i).getItem());
    }

    @Override
    public boolean hasAnyOf(ImmutableSet<MCTownItem> items) {
        return container.hasAnyOf(items.stream().map(MCTownItem::get).collect(Collectors.toSet()));
    }

    @Override
    public void setItems(List<MCTownItem> newItems) {
        for (int i = 0; i < newItems.size(); i++) {
            container.setItem(i, new ItemStack(newItems.get(i).get(), 1));
        }
    }

    @Override
    public void removeItem(
            int index,
            int amount
    ) {
        container.removeItem(index, container.getItem(index).getCount()); // TODO: Respect count on MCTownItem
    }

    @Override
    public void setItem(
            int i,
            MCTownItem item
    ) {
        container.setItem(i, new ItemStack(item.get(), 1));
    }

    @Override
    public boolean isFull() {
        for (int i = 0; i < container.getContainerSize(); i++) {
            if (container.getItem(i).isEmpty()) {
                return false;
            }
        }
        return true;
    }

    @Override
    public String toString() {
        Collection<MCTownItem> items = new ArrayList<>();
        for (int i = 0; i < container.getContainerSize(); i++) {
            items.add(getItem(i));
        }
        return "MCContainer{" +
                "container.items=" + items +
                '}';
    }
}
