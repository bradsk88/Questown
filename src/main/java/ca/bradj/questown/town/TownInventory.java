package ca.bradj.questown.town;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class TownInventory<ItemContainer, Item> {

    private final ItemTaker<ItemContainer, Item> taker;
    private final List<ItemContainer> containers = new ArrayList<>();

    public void addContainer(ItemContainer chest) {
        this.containers.add(chest);
    }

    public interface ItemTaker<ItemContainer, Item> {
        @Nullable Item takeItem(ItemContainer container, Item item);
    }

    public TownInventory(
            ItemTaker<ItemContainer, Item> taker
    ) {
        this.taker = taker;
    }

    public @Nullable Item takeItem(Item item) {
        for (ItemContainer container : this.containers) {
            Item foundItem = this.taker.takeItem(container, item);
            if (foundItem != null) {
                return foundItem;
            }
        }
        return null;
    }

}
