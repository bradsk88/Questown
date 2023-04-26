package ca.bradj.questown.logic;

import com.google.common.collect.ImmutableList;
import net.minecraft.world.item.BedItem;
import net.minecraft.world.item.crafting.Ingredient;

import java.util.Collection;

public class RoomRecipes {

    public static Collection<Ingredient> filterSpecialBlocks(
            Iterable<Ingredient> ingredients
    ) {
        ImmutableList.Builder<Ingredient> b = ImmutableList.builder();
        boolean foundBed = false;
        for (Ingredient i : ingredients) {
            if (foundBed) {
                foundBed = false;
                continue;
            }
            if (i.getItems()[0].getItem() instanceof BedItem) {
                foundBed = true;
            }
            b.add(i);
        }
        return b.build();
    }
}

