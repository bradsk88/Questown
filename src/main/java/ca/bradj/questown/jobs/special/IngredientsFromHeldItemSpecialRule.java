package ca.bradj.questown.jobs.special;

import ca.bradj.questown.integration.jobs.BeforeInitEvent;
import ca.bradj.questown.integration.jobs.JobPhaseModifier;
import ca.bradj.questown.integration.minecraft.MCHeldItem;
import ca.bradj.questown.logic.PredicateCollection;
import com.google.common.collect.ImmutableList;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import org.jetbrains.annotations.Nullable;

public class IngredientsFromHeldItemSpecialRule extends
        JobPhaseModifier {

    @Override
    public void beforeInit(BeforeInitEvent bxEvent) {
        super.beforeInit(bxEvent);
        bxEvent.replaceIngredients().accept(before -> new PredicateCollection<MCHeldItem>() {
            @Override
            public boolean isEmpty() {
                @Nullable Ingredient ing = getIngredientFromHeldItems(bxEvent.heldItems().get());
                if (ing == null) {
                    return before.isEmpty();
                }
                return ing.isEmpty();
            }

            @Override
            public boolean test(MCHeldItem mcHeldItem) {
                @Nullable Ingredient ing = getIngredientFromHeldItems(bxEvent.heldItems().get());
                if (ing == null) {
                    return before.test(mcHeldItem);
                }
                return ing.test(mcHeldItem.toItem().toItemStack());
            }
        });
    }

    private Ingredient getIngredientFromHeldItems(ImmutableList<MCHeldItem> mcHeldItems) {
        // TODO[ASAP]: Implement this
        return Ingredient.of(Items.BREAD);
    }
}
