package ca.bradj.questown.mobs.visitor;

import ca.bradj.questown.gui.Ingredients;
import ca.bradj.questown.logic.RoomRecipes;
import ca.bradj.questown.mc.Compat;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.Ingredient;
import org.jetbrains.annotations.Nullable;

/**
 * The need line that names what a townie is blocked on: the missing room, the room it cannot get
 * into, or the missing item. Shared by the on-look hint ({@code NeedBubbleFocus}) and the status
 * tooltip ({@code ServerJobsRegistry.getStatusText}), so both surfaces say the same thing.
 */
public final class TownieNeedDetail {

    private TownieNeedDetail() {
    }

    /** The named need line, or null when the need has no key (callers fall back to their own text). */
    public static @Nullable Component of(VisitorMobEntity townie) {
        ResourceLocation missingRoom = townie.getMissingRoom();
        if (missingRoom != null) {
            return Compat.translatable("message.questown.townie_need.missing_room.room", RoomRecipes.getName(missingRoom));
        }
        ResourceLocation unreachable = townie.getUnreachableRoom();
        if (unreachable != null) {
            return Compat.translatable("message.questown.townie_need.cant_reach.room", RoomRecipes.getName(unreachable));
        }
        Ingredient missing = townie.getUnmetItemIngredient();
        if (missing == null) {
            return null;
        }
        return Compat.translatable("message.questown.townie_need.unmet_item.item", Ingredients.getName(missing));
    }
}
