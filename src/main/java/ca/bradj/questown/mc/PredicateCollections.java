package ca.bradj.questown.mc;

import ca.bradj.questown.integration.minecraft.MCHeldItem;
import ca.bradj.questown.integration.minecraft.MCTownItem;
import ca.bradj.questown.logic.IPredicateCollection;
import ca.bradj.questown.logic.MonoPredicateCollection;
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
                (ingr, item) -> ingr.test(item.get().toQTItemStack()),
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
                        return v.test(itemStack.get().toQTItemStack());
                    }
                },
                IPredicateCollection::isEmpty,
                Predicate::test,
                "MC.Ingredient " + v.toJson()
        );
    }

    public static PredicateCollection<MCTownItem, ?> townify(PredicateCollection<MCHeldItem, ?> v) {
        return PredicateCollectionsClean.townify(v);
    }

    public static Map<Integer, PredicateCollection<MCHeldItem, ItemStack>> fromMCIngredientMap(ImmutableMap<Integer, Ingredient> in) {
        ImmutableMap.Builder<Integer, PredicateCollection<MCHeldItem, ItemStack>> builder = ImmutableMap.builder();
        in.forEach((k, v) -> builder.put(k, fromMCIngredient(v)));
        return builder.build();
    }

    public static <X> MonoPredicateCollection<X> fromSimple(Predicate<X> simple) {
        return new MonoPredicateCollection<>(new IPredicateCollection<X>() {
            @Override
            public boolean isEmpty() {
                return false;
            }

            @Override
            public boolean test(X x) {
                return simple.test(x);
            }
        }, "from simple predicate");
    }

    public static <X> MonoPredicateCollection<X> fromSimpleEqualityCheck(X value) {
        return fromSimple(value::equals);
    }
}
