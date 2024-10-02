package ca.bradj.questown.jobs.special;

import ca.bradj.questown.integration.jobs.BeforeInitEvent;
import ca.bradj.questown.integration.jobs.JobPhaseModifier;
import ca.bradj.questown.integration.minecraft.MCHeldItem;
import ca.bradj.questown.integration.minecraft.MCTownItem;
import ca.bradj.questown.jobs.Jobs;
import ca.bradj.questown.logic.IPredicateCollection;
import ca.bradj.questown.logic.PredicateCollection;
import ca.bradj.questown.mc.PredicateCollections;
import com.google.common.collect.ImmutableList;
import joptsimple.internal.Strings;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import org.jetbrains.annotations.Nullable;

import java.util.function.Consumer;
import java.util.function.Function;

public class IngredientsFromHeldItemSpecialRule extends
        JobPhaseModifier {

    private final boolean isTool;

    public IngredientsFromHeldItemSpecialRule(boolean isTool) {
        this.isTool = isTool;
    }

    @Override
    public void beforeInit(BeforeInitEvent bxEvent) {
        super.beforeInit(bxEvent);
        if (isTool) {
            bxEvent.replaceTools().accept(before -> PredicateCollection.wrap(
                    before,
                    (IPredicateCollection<MCTownItem> inner) -> {
                        @Nullable Ingredient ing = getIngredientFromHeldItems(bxEvent.heldItems().get());
                        if (ing == null) {
                            return inner.isEmpty();
                        }
                        return ing.isEmpty();
                    },
                    (IPredicateCollection<MCTownItem> inner, MCTownItem mcHeldItem) -> {
                        @Nullable Ingredient ing = getIngredientFromHeldItems(bxEvent.heldItems().get());
                        if (ing == null) {
                            return before.test(mcHeldItem);
                        }
                        return ing.test(mcHeldItem.toItemStack());
                    },
                    "Tool from held item, replacing"
            ));
        } else {
            bxEvent.replaceIngredients().accept(before -> PredicateCollection.wrap(
                    before,
                    (IPredicateCollection<MCHeldItem> inner) -> {
                        @Nullable Ingredient ing = getIngredientFromHeldItems(bxEvent.heldItems().get());
                        if (ing == null) {
                            return inner.isEmpty();
                        }
                        return ing.isEmpty();
                    },
                    (IPredicateCollection<MCHeldItem> inner, MCHeldItem mcHeldItem) -> {
                        if (mcHeldItem.isEmpty()) {
                            return false;
                        }
                        @Nullable Ingredient ing = getIngredientFromHeldItems(bxEvent.heldItems().get());
                        if (ing == null) {
                            return before.test(mcHeldItem);
                        }
                        return ing.test(mcHeldItem.toItem().toItemStack());
                    },
                    "Ingredient from held item, replacing"
            ));
        }
    }

    private Ingredient getIngredientFromHeldItems(ImmutableList<MCHeldItem> mcHeldItems) {
        // TODO[ASAP]: Implement this
        return Ingredient.of(Items.BREAD);
    }
}
