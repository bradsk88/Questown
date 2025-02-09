package ca.bradj.questown.integration.minecraft;

import ca.bradj.questown.jobs.leaver.ContainerTarget;
import ca.bradj.questown.jobs.leaver.RankBoost;
import com.google.common.collect.ImmutableList;
import net.minecraft.world.Container;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;

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
    public MCTownItem removeItem(
            int index
    ) {
        return MCTownItem.fromMCItemStack(container.removeItem(index, 1));
    }

    @Override
    public boolean setItem(
            int i,
            MCTownItem item
    ) {
        container.setItem(i, item.toMCItemStack());
        return true;
    }

    @Override
    public boolean isFull(
            ) {
        for (int i = 0; i < container.getContainerSize(); i++) {
            if (container.getItem(i).isEmpty()) {
                return false;
            }
        }
        return true;
    }

    @Override
    public String toShortString() {
        return toShortString(true);
    }

    @Override
    public String toShortString(boolean includeAir) {
        ImmutableList.Builder<String> names = ImmutableList.builder();
        for (int i = 0; i < container.getContainerSize(); i++) {
            Item item = container.getItem(i).getItem();
            if (Items.AIR.equals(item)) {
                if (!includeAir) {
                    continue;
                }
            }
            if (ForgeRegistries.ITEMS.getKey(item) == null) {
                names.add("<No ID>");
            } else {
                names.add(ForgeRegistries.ITEMS.getKey(item).getPath());
            }
        }
        return String.join(", ", names.build());
    }

    @Override
    public boolean canAcceptIfSpaceAllows(MCTownItem item) {
        return true;
    }

    @Override
    public RankBoost getItemAcceptanceRankBoost() {
        return RankBoost.SAME_AS_VANILLA_CHEST;
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
