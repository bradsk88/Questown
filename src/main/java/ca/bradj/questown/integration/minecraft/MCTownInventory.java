package ca.bradj.questown.integration.minecraft;

import ca.bradj.questown.town.TownInventory;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;

public class MCTownInventory extends TownInventory<Container, MCTownItem> {
    public MCTownInventory() {
        super((container, item) -> {
            for (int i = 0; i < container.getContainerSize(); i++) {
                if (container.getItem(i).getItem().equals(item.get())) {
                    ItemStack taken = container.removeItem(i, 1);
                    return new MCTownItem(taken.getItem());
                }
            }
            return null;
        });
    }
}
