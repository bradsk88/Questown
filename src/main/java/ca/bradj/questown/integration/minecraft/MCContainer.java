package ca.bradj.questown.integration.minecraft;

import ca.bradj.questown.jobs.leaver.ContainerTarget;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

public class MCContainer implements ContainerTarget.Container<MCTownItem> {

    private final Container container;

    public MCContainer(@NotNull Container container) {
        this.container = container;
    }

    @Override
    public int size() {
        return container.getContainerSize();
    }

    @Override
    public MCTownItem getItem(int i) {
        ItemStack cItem = container.getItem(i);
        return MCTownItem.fromMCItemStack(cItem);
    }

    @Override
    public boolean hasAnyOf(ImmutableSet<MCTownItem> items) {
        return container.hasAnyOf(items.stream().map(MCTownItem::get).collect(Collectors.toSet()));
    }

    @Override
    public void setItems(List<MCTownItem> newItems) {
        for (int i = 0; i < newItems.size(); i++) {
            container.setItem(i, newItems.get(i).toItemStack());
        }
    }

    @Override
    public void removeItem(
            int index,
            int amount
    ) {
        container.removeItem(index, amount);
    }

    @Override
    public void setItem(
            int i,
            MCTownItem item
    ) {
        container.setItem(i, item.toItemStack());
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
    public String toShortString() {
        ImmutableList.Builder<String> names = ImmutableList.builder();
        for (int i = 0; i < container.getContainerSize(); i++) {
            if (ForgeRegistries.ITEMS.getKey(container.getItem(i).getItem()) == null) {
                names.add("<No ID>");
            } else {
                names.add(ForgeRegistries.ITEMS.getKey(container.getItem(i).getItem()).getPath());
            }
        }
        return String.join(", ", names.build());
    }

    @Override
    public String toString() {
        Collection<String> items = new ArrayList<>();
        for (int i = 0; i < container.getContainerSize(); i++) {
            items.add(getItem(i).getShortName());
        }
        return "MCContainer{" +
                "container.items=" + items +
                '}';
    }
}
