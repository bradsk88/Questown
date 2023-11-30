package ca.bradj.questown.jobs;

import ca.bradj.questown.QT;
import com.google.common.collect.ImmutableList;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;

import javax.annotation.Nullable;

public class WorkRequest {

    private final @Nullable TagKey<Item> tag;
    private final @Nullable Item item;

    public WorkRequest(
            @Nullable TagKey<Item> tag,
            @Nullable Item item
    ) {
        this.tag = tag;
        this.item = item;
    }
    // private final int quantity; // TODO: Implement

    // For example, if the player requests
    public ImmutableList<Ingredient> getAllInterpretationsForGUI() {
        if (tag == null) {
            return ImmutableList.of(Ingredient.of(item));
        }
        if (item == null) {
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

    // TODO[ASAP]: Good toString / Equals
}
