package ca.bradj.questown.integration.minecraft;

import ca.bradj.questown.jobs.GathererJournal;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

public class MCTownItem implements GathererJournal.Item {

    Item item;

    public MCTownItem(Item item) {
        this.item = item;
    }

    @Override
    public boolean isEmpty() {
        return Items.AIR.equals(item);
    }

    @Override
    public boolean isFood() {
        return Items.BREAD.equals(item);
    }

    public Item get() {
        return item;
    }

    @Override
    public String toString() {
        return "MCTownItem{" +
                "item=" + item +
                '}';
    }
}
