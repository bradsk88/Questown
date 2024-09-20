package ca.bradj.questown.mc;

import ca.bradj.questown.integration.minecraft.MCHeldItem;
import ca.bradj.questown.integration.minecraft.MCTownItem;
import ca.bradj.questown.logic.IPredicateCollection;
import ca.bradj.questown.logic.PredicateCollection;
import com.google.common.collect.ImmutableMap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;

import java.util.Map;
import java.util.function.Predicate;

public class PredicateCollections {
    public static PredicateCollection<MCHeldItem, ItemStack> fromMCIngredient(Ingredient v) {
        return PredicateCollection.<MCHeldItem, ItemStack> wrap(
                new IPredicateCollection<ItemStack>() {
                    @Override
                    public boolean isEmpty() {
                        return v.isEmpty();
                    }

                    @Override
                    public boolean test(ItemStack itemStack) {
                        return v.test(itemStack);
                    }
                },
                IPredicateCollection::isEmpty,
                (ingr, item) -> ingr.test(item.get().toItemStack()),
                "MC.Ingredient " + v.toJson()
        );
    }
    public static PredicateCollection<MCHeldItem, MCHeldItem> fromMCIngredient2(Ingredient v) {
        return PredicateCollection.<MCHeldItem, MCHeldItem> wrap(
                new IPredicateCollection<MCHeldItem>() {
                    @Override
                    public boolean isEmpty() {
                        return v.isEmpty();
                    }

                    @Override
                    public boolean test(MCHeldItem itemStack) {
                        return v.test(itemStack.get().toItemStack());
                    }
                },
                IPredicateCollection::isEmpty,
                Predicate::test,
                "MC.Ingredient " + v.toJson()
        );
    }

    public static PredicateCollection<MCTownItem, ?> townify(PredicateCollection<MCHeldItem, ?> v) {
        return PredicateCollection.wrap(
                new IPredicateCollection<MCTownItem>() {
                    @Override
                    public boolean isEmpty() {
                        return v.isEmpty();
                    }

                    @Override
                    public boolean test(MCTownItem itemStack) {
                        return v.test(MCHeldItem.fromTown(itemStack));
                    }
                },
                IPredicateCollection::isEmpty,
                Predicate::test,
                "Town-as-Held"
        );
    }

    public static Map<Integer, PredicateCollection<MCHeldItem, ItemStack>> fromMCIngredientMap(ImmutableMap<Integer, Ingredient> in) {
        ImmutableMap.Builder<Integer, PredicateCollection<MCHeldItem, ItemStack>> builder = ImmutableMap.builder();
        in.forEach((k, v) -> builder.put(k, fromMCIngredient(v)));
        return builder.build();
    }
}
