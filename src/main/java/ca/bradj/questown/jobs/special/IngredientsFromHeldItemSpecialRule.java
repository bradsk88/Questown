package ca.bradj.questown.jobs.special;

import ca.bradj.questown.integration.jobs.BeforeInitEvent;
import ca.bradj.questown.integration.jobs.ItemCheck;
import ca.bradj.questown.integration.jobs.JobPhaseModifier;
import ca.bradj.questown.integration.minecraft.MCHeldItem;
import ca.bradj.questown.integration.minecraft.MCTownItem;
import ca.bradj.questown.items.StockRequestItem;
import ca.bradj.questown.jobs.requests.WorkRequest;
import net.minecraft.world.item.crafting.Ingredient;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;

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
            bxEvent.replaceTools().replace(before -> new ItemCheck<>() {
                @Override
                public boolean isEmpty(Collection<MCHeldItem> heldItems) {
                    @Nullable Ingredient ing = getIngredientFromHeldItems(heldItems);
                    if (ing == null) {
                        if (before == null) {
                            return true;
                        }
                        return before.isEmpty(heldItems);
                    }
                    return ing.isEmpty();
                }

                @Override
                public boolean test(
                        Collection<MCHeldItem> heldItems,
                        MCTownItem item
                ) {
                    @Nullable Ingredient ing = getIngredientFromHeldItems(heldItems);
                    if (ing == null) {
                        if (before == null) {
                            return false;
                        }
                        return before.test(heldItems, item);
                    }
                    return ing.test(item.toItemStack());
                }
            });
        } else {
            bxEvent.replaceIngredients().replace(before -> new ItemCheck<>() {
                @Override
                public boolean isEmpty(Collection<MCHeldItem> heldItems) {
                    @Nullable Ingredient ing = getIngredientFromHeldItems(heldItems);
                    if (ing == null) {
                        return before.isEmpty(heldItems);
                    }
                    return ing.isEmpty();
                }

                @Override
                public boolean test(
                        Collection<MCHeldItem> heldItems,
                        MCHeldItem item
                ) {
                    if (item.isEmpty()) {
                        return false;
                    }
                    @Nullable Ingredient ing = getIngredientFromHeldItems(heldItems);
                    if (ing == null) {
                        return before.test(heldItems, item);
                    }
                    return ing.test(item.toItem().toItemStack());
                }
            });
        }
    }

    private Ingredient getIngredientFromHeldItems(Collection<MCHeldItem> mcHeldItems) {
        for (MCHeldItem i : mcHeldItems) {
            if (i.get().get() instanceof StockRequestItem) {
                WorkRequest req = StockRequestItem.getRequest(i.getItemNBTData());
                if (req != null) {
                    return req.asIngredient();
                }
            }
        }
        return null;
    }
}
