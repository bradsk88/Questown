package ca.bradj.questown.integration.minecraft;

import ca.bradj.questown.jobs.leaver.RankBoost;
import ca.bradj.questown.mc.Util;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;

public class MCContainer implements MCContainerInterface {

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
        return MCContainer.toShortString(container, includeAir);
    }

    private static String toShortString(
            Container container,
            boolean includeAir
    ) {
        return Util.toShortString(container.getContainerSize(), container::getItem, includeAir);
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
