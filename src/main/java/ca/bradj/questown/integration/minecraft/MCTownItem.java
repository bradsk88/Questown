package ca.bradj.questown.integration.minecraft;

import ca.bradj.questown.core.init.TagsInit;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.Objects;
import java.util.function.Consumer;

public class MCTownItem implements ca.bradj.questown.jobs.Item<MCTownItem> {

    // TODO: Add "given by" field to prevent villager from dumping user-given items back into chests

    private final int quantity;
    private final Item item;
    private final CompoundTag nbt;

    public static MCTownItem fromMCItemStack(ItemStack i) {
        return new MCTownItem(i.getItem(), i.getCount(), i.serializeNBT());
    }

    public static MCTownItem of(CompoundTag tag) {
        CompoundTag nbt = tag.getCompound("item");
        ItemStack stack = ItemStack.of(nbt);
        return new MCTownItem(stack.getItem(), stack.getCount(), nbt);
    }

    public MCTownItem(
            Item item,
            int quantity,
            CompoundTag nbt
    ) {
        this.quantity = quantity;
        this.item = item;
        this.nbt = nbt;
    }

    public MCTownItem copy() {
        return new MCTownItem(item, quantity, nbt.copy());
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
                "quantity=" + quantity +
                ", item=" + item +
                ", nbt=" + nbt +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        MCTownItem that = (MCTownItem) o;
        if (this.isEmpty() && that.isEmpty()) {
            return true;
        }
        return isSameIgnoringNBT(that) && Objects.equals(nbt, that.nbt);
    }

    public boolean isSameIgnoringNBT(MCTownItem that) {
        return quantity == that.quantity && Objects.equals(item, that.item);
    }

    @Override
    public int hashCode() {
        return Objects.hash(quantity, item, nbt);
    }

    public static MCTownItem Air() {
        return new MCTownItem(Items.AIR, 1, new CompoundTag());
    }

    public CompoundTag serializeNBT() {
        CompoundTag tag = new CompoundTag();
        tag.put("item", nbt);
        return tag;
    }

    public MCTownItem shrink() {
        if (quantity == 1) {
            return MCTownItem.Air();
        }
        ItemStack stack = toItemStack();
        stack.shrink(1);
        return new MCTownItem(stack.getItem(), stack.getCount(), stack.serializeNBT());
    }

    @Override
    public MCTownItem unit() {
        return withQuantity(1);
    }

    public MCTownItem withQuantity(int qy) {
        ItemStack stack = toItemStack();
        stack.setCount(qy);
        return new MCTownItem(stack.getItem(), qy, stack.serializeNBT());
    }

    @Override
    public int quantity() {
        return quantity;
    }

    @Override
    public String getShortName() {
        String name = "[unknown]";
        ResourceLocation registryName = ForgeRegistries.ITEMS.getKey(get());
        if (registryName != null) {
            name = registryName.toString();
        }
        if (quantity > 1) {
            name = String.format("%sx%s", quantity, name);
        }
        return name;
    }

    public ItemStack toItemStack() {
        return ItemStack.of(nbt);
    }

    public boolean hasEmptyNBT() {
        CompoundTag copy = nbt.copy();
        if (copy.contains("id")) {
            copy.remove("id");
        }
        if (copy.contains("Count")) {
            copy.remove("Count");
        }
        if (copy.contains("tag")) {
            if (copy.getCompound("tag").isEmpty()) {
                copy.remove("tag");
            }
        }
        return copy.isEmpty();
    }

    public void setNBT(Consumer<CompoundTag> adder) {
        adder.accept(nbt.getCompound("tag"));
    }

    public CompoundTag getItemNBT() {
        return nbt.copy().getCompound("tag");
    }
}
