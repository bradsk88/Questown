package ca.bradj.questown.items;

import ca.bradj.questown.Questown;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

public class EffectMetaItem extends Item {
    public static final String ITEM_ID = "effect_meta_item";

    public EffectMetaItem() {
        super(Questown.DEFAULT_ITEM_PROPS);
    }

    public static ItemStack applyEffect(ItemStack stack, ResourceLocation value) {
        QTNBT.put(stack.getOrCreateTag(), "effect", value);
        return stack;
    }

    public static ResourceLocation getEffect(ItemStack itemStack) {
        return QTNBT.getResourceLocation(itemStack.getOrCreateTag(), "effect");
    }

    public static class Effects {
        public static final ResourceLocation FILL_HUNGER = Questown.ResourceLocation("fill_hunger");
        public static final ResourceLocation FILL_HUNGER_ANGRY = Questown.ResourceLocation("fill_hunger_angry");
    }

}
