package ca.bradj.questown.items;

import ca.bradj.questown.Questown;
import ca.bradj.questown.core.init.items.ItemsInit;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

public class EffectMetaItem extends Item {
    public static final String ITEM_ID = "effect_meta_item";

    public EffectMetaItem() {
        super(Questown.DEFAULT_ITEM_PROPS);
    }

    public static ItemStack applyEffect(ItemStack stack, ResourceLocation value, long duration) {
        QTNBT.put(stack.getOrCreateTag(), "effect", value);
        QTNBT.putLong(stack.getOrCreateTag(), "effect_duration", duration);
        return stack;
    }

    public static ResourceLocation getEffect(ItemStack itemStack) {
        return QTNBT.getResourceLocation(itemStack.getOrCreateTag(), "effect");
    }

    public static Long getEffectExpiry(ItemStack s, long tick) {
        return QTNBT.getLong(s.getOrCreateTag(), "effect_duration") + tick;
    }

    public static ItemStack withConsumableEffect(ResourceLocation effect) {
        return applyEffect(ItemsInit.EFFECT.get().getDefaultInstance(), effect, 1);
    }

    public static ItemStack withLastingEffect(ResourceLocation effect, long duration) {
        return applyEffect(ItemsInit.EFFECT.get().getDefaultInstance(), effect, duration);
    }

    public static class ConsumableEffects {
        public static final ResourceLocation FILL_HUNGER = Questown.ResourceLocation("fill_hunger");
        public static final ResourceLocation FILL_HUNGER_HALF = Questown.ResourceLocation("fill_hunger_half");
    }

    public static class MoodEffects {
        public static final ResourceLocation UNCOMFORTABLE_EATING = Questown.ResourceLocation("uncomfortable_eating");
        public static final ResourceLocation COMFORTABLE_EATING = Questown.ResourceLocation("comfortable_eating");
        public static final ResourceLocation ATE_RAW_FOOD = Questown.ResourceLocation("are_raw_food");
    }

}
