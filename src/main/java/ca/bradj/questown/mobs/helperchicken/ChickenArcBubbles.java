package ca.bradj.questown.mobs.helperchicken;

import ca.bradj.questown.Questown;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/** The item icons above the helper chicken for each lesson, and whether they show through walls. Two icons alternate every second. */
public final class ChickenArcBubbles {

    public static final ResourceLocation SUNSET_TEXTURE = new ResourceLocation(
            Questown.MODID, "textures/bubble/sunset.png"
    );

    public record Bubble(
            ItemStack iconA,
            ItemStack iconB,
            boolean throughWalls,
            ResourceLocation textureIcon
    ) {
        public static final Bubble HIDDEN = new Bubble(ItemStack.EMPTY, ItemStack.EMPTY, false, null);

        public Bubble(ItemStack iconA, ItemStack iconB, boolean throughWalls) {
            this(iconA, iconB, throughWalls, null);
        }

        public static Bubble single(ItemStack icon) {
            return new Bubble(icon, ItemStack.EMPTY, false);
        }

        public static Bubble alternating(ItemStack a, ItemStack b) {
            return new Bubble(a, b, false);
        }

        public static Bubble throughWalls(ItemStack icon) {
            return new Bubble(icon, ItemStack.EMPTY, true);
        }

        /** A bubble showing a texture instead of an item. */
        public static Bubble texture(ResourceLocation texture) {
            return new Bubble(ItemStack.EMPTY, ItemStack.EMPTY, false, texture);
        }
    }

    private ChickenArcBubbles() {
    }
}
