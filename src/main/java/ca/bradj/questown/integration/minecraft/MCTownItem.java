package ca.bradj.questown.integration.minecraft;

import ca.bradj.questown.core.init.TagsInit;
import ca.bradj.questown.loader.Loader;
import ca.bradj.questown.mc.Compat;
import com.google.common.collect.ImmutableSet;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.crafting.Ingredient;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;
import java.util.function.Consumer;

public class MCTownItem implements ca.bradj.questown.jobs.Item<MCTownItem> {

    // TODO: Add "given by" field to prevent villager from dumping user-given items back into chests

    private final int quantity;
    private final Item item;
    /** the stack's CUSTOM_DATA (1.20.5+ data components) — the only per-item payload this model keeps */
    private final CompoundTag nbt;

    public static MCTownItem fromMCItemStack(ItemStack i) {
        return new MCTownItem(i.getItem(), i.getCount(), customData(i));
    }

    public static MCTownItem of(CompoundTag tag) {
        CompoundTag itemTag = tag.getCompound("item");
        Item item;
        int quantity;
        CompoundTag nbt;
        if (itemTag.contains("item", Tag.TAG_STRING)) {
            // New format (1.20.6): {item: <name>, count: <int>, data: <compound>}
            item = Compat.itemFromId(new ResourceLocation(itemTag.getString("item")));
            quantity = itemTag.getInt("count");
            nbt = itemTag.getCompound("data");
        } else if (itemTag.contains("id")) {
            // Legacy format (1.19): {id: <name>, Count: <int>, tag: <compound>}
            item = Compat.itemFromId(new ResourceLocation(itemTag.getString("id")));
            quantity = itemTag.getInt("Count");
            nbt = itemTag.getCompound("tag");
        } else {
            item = Items.AIR;
            quantity = 0;
            nbt = new CompoundTag();
        }
        return new MCTownItem(item == null ? Items.AIR : item, quantity, nbt);
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

    public static ImmutableSet<MCTownItem> fromIngredient(Ingredient i) {
        ImmutableSet.Builder<MCTownItem> b = ImmutableSet.builder();
        for (ItemStack item : i.getItems()) {
            b.add(fromMCItemStack(item));
        }
        return b.build();
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
        CompoundTag item = new CompoundTag();
        ResourceLocation name = Loader.itemIdOf(this.item);
        if (name == null) {
            name = new ResourceLocation("minecraft:air");
        }
        item.putString("item", name.toString());
        item.putInt("count", quantity);
        item.put("data", nbt);
        CompoundTag tag = new CompoundTag();
        tag.put("item", item);
        return tag;
    }

    public MCTownItem shrink() {
        if (quantity == 1) {
            return MCTownItem.Air();
        }
        ItemStack stack = toQTItemStack();
        stack.shrink(1);
        return new MCTownItem(stack.getItem(), stack.getCount(), customData(stack));
    }

    @Override
    public MCTownItem unit() {
        return withQuantity(1);
    }

    public MCTownItem withQuantity(int qy) {
        ItemStack stack = toQTItemStack();
        stack.setCount(qy);
        return new MCTownItem(stack.getItem(), qy, customData(stack));
    }

    @Override
    public int quantity() {
        return quantity;
    }

    @Override
    public String getShortName() {
        String name = "[unknown]";
        ResourceLocation registryName = Loader.itemIdOf(get());
        if (registryName != null) {
            name = registryName.toString();
        }
        if (quantity > 1) {
            name = String.format("%sx%s", quantity, name);
        }
        return name;
    }

    public enum StackPolicy {
        KEEP_QT_NBT,
        STRIP_QT_NBT
    }

    public ItemStack toQTItemStack() {
        return toItemStack(StackPolicy.KEEP_QT_NBT);
    }

    public ItemStack toMCItemStack() {
        return toItemStack(StackPolicy.STRIP_QT_NBT);
    }

    private ItemStack toItemStack(StackPolicy preserveQTNBT) {
        ItemStack newOne = new ItemStack(item, quantity);
        if (!newOne.getItem().equals(item)) {
            // item id was unregistered; an AIR stack carries the count
            newOne = new ItemStack(Items.AIR, quantity);
        }
        CompoundTag data = (preserveQTNBT == StackPolicy.STRIP_QT_NBT) ? stripQTNBT(nbt) : nbt;
        if (!data.isEmpty()) {
            newOne.set(DataComponents.CUSTOM_DATA, CustomData.of(data));
        }
        return newOne;
    }

    public boolean hasEmptyNBT() {
        CompoundTag copy = stripQTNBT(nbt);
        return copy.isEmpty();
    }

    private @NotNull CompoundTag stripQTNBT(CompoundTag in) {
        CompoundTag copy = in.copy();
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
        return copy;
    }

    public void setNBT(Consumer<CompoundTag> adder) {
        adder.accept(nbt);
    }

    public CompoundTag getItemNBT() {
        return nbt.copy();
    }

    private static CompoundTag customData(ItemStack stack) {
        return stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
    }
}
