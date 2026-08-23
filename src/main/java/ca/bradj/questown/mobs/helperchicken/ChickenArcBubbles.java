package ca.bradj.questown.mobs.helperchicken;

import ca.bradj.questown.Questown;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/**
 * Pure mapping from {@link ChickenBeatState} to a {@link Bubble} — the pair of
 * item icons shown above the chicken's head plus the through-walls flag.
 *
 * <p>v1 renders bubbles as {@link ItemStack}s via the vanilla item renderer
 * (see {@link ca.bradj.questown.render.BubbleRenderer}). Each beat picks items that visually
 * convey the next action — a stick icon for "go get a stick", a chest for
 * "place a chest", and so on. When a beat has two icons the layer hard-cuts
 * between them every 20 ticks; when it has one the layer shows it steadily.
 *
 * <p>{@link ChickenBubbleIcon} is the authored-texture inventory (U2 assets
 * and future PNG-based icons). This class does not go through that enum
 * because v1's renderer only supports {@code ItemStack}, and some beats
 * (the wall-block beat) have no matching authored texture.
 */
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

        /**
         * Texture-mode bubble: the layer renders {@code texture} as a flat
         * quad above the chicken instead of an item icon. Used for authored
         * assets without a vanilla item equivalent.
         */
        public static Bubble texture(ResourceLocation texture) {
            return new Bubble(ItemStack.EMPTY, ItemStack.EMPTY, false, texture);
        }
    }

    private ChickenArcBubbles() {
    }
}
