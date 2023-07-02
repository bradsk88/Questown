package ca.bradj.questown.integration.minecraft;

import ca.bradj.questown.core.init.TagsInit;
import ca.bradj.questown.jobs.GathererJournal;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;

public class MCTownItem implements GathererJournal.Item {

    // TODO: Add "given by" field to prevent villager from dumping user-given items back into chests

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
        return Ingredient.of(TagsInit.Items.VILLAGER_FOOD).test(new ItemStack(item));
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
