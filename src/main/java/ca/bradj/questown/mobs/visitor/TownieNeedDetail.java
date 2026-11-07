package ca.bradj.questown.mobs.visitor;

import ca.bradj.questown.gui.Ingredients;
import ca.bradj.questown.logic.RoomRecipes;
import ca.bradj.questown.mc.Compat;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.Ingredient;
import org.jetbrains.annotations.Nullable;

/** The line that names what a villager is blocked on, for both the on-look hint and the status tooltip. */
public final class TownieNeedDetail {

    private TownieNeedDetail() {
    }

    /** The line, or null when the need has no key. */
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
