package ca.bradj.questown.mobs.helperchicken;

import ca.bradj.questown.core.init.BlocksInit;
import ca.bradj.questown.core.init.items.ItemsInit;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Pure mapping from {@link ChickenBeatState} to a {@link Bubble} — the pair of
 * item icons shown above the chicken's head plus the through-walls flag.
 *
 * <p>v1 renders bubbles as {@link ItemStack}s via the vanilla item renderer
 * (see {@link HelperChickenBubbleLayer}). Each beat picks items that visually
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

    public record Bubble(
            ItemStack iconA,
            ItemStack iconB,
            boolean throughWalls
    ) {
        public static final Bubble HIDDEN = new Bubble(ItemStack.EMPTY, ItemStack.EMPTY, false);

        public static Bubble single(ItemStack icon) {
            return new Bubble(icon, ItemStack.EMPTY, false);
        }

        public static Bubble alternating(ItemStack a, ItemStack b) {
            return new Bubble(a, b, false);
        }

        public static Bubble throughWalls(ItemStack icon) {
            return new Bubble(icon, ItemStack.EMPTY, true);
        }
    }

    private ChickenArcBubbles() {
    }

    public static Bubble forState(ChickenBeatState state) {
        return switch (state) {
            case WAITING_FOR_STICK -> Bubble.single(new ItemStack(Items.STICK));
            case WAITING_FOR_WAND_ON_CAMPFIRE -> Bubble.alternating(
                    new ItemStack(ItemsInit.TOWN_WAND.get()),
                    new ItemStack(Items.CAMPFIRE)
            );
            case SUNSET_AND_MAP -> Bubble.alternating(
                    new ItemStack(Items.CLOCK),
                    new ItemStack(Items.MAP)
            );
            case WAITING_FOR_WALL_BLOCK -> Bubble.single(new ItemStack(Items.COBBLESTONE));
            case WAITING_FOR_DOOR -> Bubble.single(new ItemStack(Items.OAK_DOOR));
            case WAITING_FOR_WAND_ON_DOOR -> Bubble.alternating(
                    new ItemStack(ItemsInit.TOWN_WAND.get()),
                    new ItemStack(Items.OAK_DOOR)
            );
            case WAITING_FOR_SIGN -> Bubble.single(new ItemStack(Items.OAK_SIGN));
            case WAITING_FOR_CHEST -> Bubble.single(new ItemStack(Items.CHEST));
            case WAITING_FOR_PRESSURE_PLATE -> Bubble.single(
                    new ItemStack(ItemsInit.WELCOME_MAT_BLOCK.get())
            );
            case WAITING_FOR_VILLAGER_UI -> Bubble.single(new ItemStack(Items.VILLAGER_SPAWN_EGG));
            case WAITING_FOR_FLAG_UI -> Bubble.single(
                    new ItemStack(BlocksInit.COBBLESTONE_TOWN_FLAG.get())
            );
            case AWAITING_WORLDLY_SEEDS_DELIVERY -> Bubble.throughWalls(
                    new ItemStack(ItemsInit.WORLDLY_SEEDS.get())
            );
            case COMPLETE, FORFEIT -> Bubble.HIDDEN;
        };
    }
}
