package ca.bradj.questown.jobs.requests;

import ca.bradj.questown.QT;
import ca.bradj.questown.jobs.gatherer.NewLeaverWork;
import com.google.common.collect.ImmutableList;
import net.minecraft.core.Registry;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraftforge.registries.ForgeRegistries;

import javax.annotation.Nullable;
import java.util.Objects;

public class WorkRequest {

    final @Nullable TagKey<Item> tag;
    final @Nullable Item item;
    private @Nullable NewLeaverWork.TagsCriteria criteria;

    private WorkRequest(
            @Nullable TagKey<Item> tag,
            @Nullable Item item,
            @Nullable NewLeaverWork.TagsCriteria criteria
    ) {
        this.tag = tag;
        this.item = item;
        this.criteria = criteria;
    }

    public static WorkRequest of(
            Item requested,
            @Nullable NewLeaverWork.TagsCriteria criteria
    ) {
        return new WorkRequest(null, requested, criteria);
    }

    public static WorkRequest of(
            TagKey<Item> tk,
            @Nullable NewLeaverWork.TagsCriteria criteria
    ) {
        return new WorkRequest(tk, null, criteria);
    }
    // private final int quantity; // TODO: Implement

    // For example, if the player requests
    public ImmutableList<Ingredient> getAllInterpretationsForGUI() {
        if (item != null) {
            return ImmutableList.of(Ingredient.of(item));
        }
        if (tag != null) {
            ImmutableList.Builder<Ingredient> b = ImmutableList.builder();
            Ingredient all = Ingredient.of(tag);
            b.add(all);
            for (ItemStack i : all.getItems()) {
                b.add(Ingredient.of((i)));
            }
        }
        QT.GUI_LOGGER.error("WorkRequest has null tag AND item");
        return ImmutableList.of();
    }

    public Ingredient asIngredient() {
        if (item != null) {
            return Ingredient.of(item);
        }
        if (tag != null) {
            return Ingredient.of(tag);
        }
        QT.GUI_LOGGER.error("WorkRequest has null tag AND item");
        return Ingredient.of();
    }

    public void toNetwork(FriendlyByteBuf buffer) {
        buffer.writeUtf(item == null ? "" : ForgeRegistries.ITEMS.getKey(item).toString());
        buffer.writeUtf(tag == null ? "" : tag.location().toString());
    }

    public static WorkRequest fromNetwork(FriendlyByteBuf buffer) {
        String i = buffer.readUtf();
        String t = buffer.readUtf();
        if (i.isEmpty() && t.isEmpty()) {
            throw new IllegalArgumentException("Invalid work request on buffer.");
        }
        // Should we include the criteria when we send across the network?
        // As far as I know, the network is only used for the UI and there's no reason to surface criteria there
        if (i.isEmpty()) {
            return new WorkRequest(new TagKey<>(Registry.ITEM_REGISTRY, new ResourceLocation(t)), null, null);
        }
        return new WorkRequest(null, ForgeRegistries.ITEMS.getValue(new ResourceLocation(i)), null);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        WorkRequest that = (WorkRequest) o;
        return Objects.equals(tag, that.tag) && Objects.equals(item, that.item);
    }

    @Override
    public int hashCode() {
        return Objects.hash(tag, item);
    }

    @Override
    public String toString() {
        return "WorkRequest{" +
                "tag=" + tag +
                ", item=" + item +
                '}';
    }

    public Component getName() {
        if (tag != null) {
            return Component.translatable("#" + tag.location());
        }
        if (item != null) {
            return Component.translatable(ForgeRegistries.ITEMS.getKey(item).toString());
        }
        return Component.translatable("invalid.workrequest");
    }

    public NewLeaverWork.TagsCriteria criteria() {
        return criteria;
    }
}
