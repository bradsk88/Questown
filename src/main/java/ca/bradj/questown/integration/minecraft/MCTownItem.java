package ca.bradj.questown.integration.minecraft;

import ca.bradj.questown.core.init.TagsInit;
import ca.bradj.questown.jobs.GathererJournal;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;

import java.util.Objects;

public class MCTownItem implements GathererJournal.Item {

    // TODO: Add "given by" field to prevent villager from dumping user-given items back into chests

    Item item; // FIXME: ItemStack so NBT, quantity is preserved

    public static MCTownItem fromMCItemStack(ItemStack i) {
        return new MCTownItem(i.getItem());
    }

    public MCTownItem(Item item) {
        this.item = item;
    }

    public static MCTownItem of(CompoundTag tag) {
        return new MCTownItem(ItemStack.of(tag).getItem());
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

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        MCTownItem that = (MCTownItem) o;
        return Objects.equals(item, that.item);
    }

    @Override
    public int hashCode() {
        return Objects.hash(item);
    }

    public static MCTownItem Air() {
        return new MCTownItem(Items.AIR);
    }

    public Tag serializeNBT() {
        CompoundTag tag = new CompoundTag();
        tag.put("item", new ItemStack(item, 1).serializeNBT()); // TODO: Quantity
        return tag;
    }
}
