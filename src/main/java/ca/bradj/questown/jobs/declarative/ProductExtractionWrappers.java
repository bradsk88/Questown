package ca.bradj.questown.jobs.declarative;

import ca.bradj.questown.QT;
import ca.bradj.questown.jobs.SpecialRules;
import ca.bradj.questown.town.AbstractWorkStatusStore;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

import java.util.Collection;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Supplier;

public class ProductExtractionWrappers {

    public record Ctx<TOWN, ITEM>(
            Supplier<ITEM> lastInsertedIngredients,
            Supplier<Integer> ingredientQuantityRequired,
            BiFunction<TOWN, ITEM, TOWN> addToNearbyChest,
            BiFunction<ITEM, Integer, ITEM> createStackWithQuantity,
            BiFunction<TOWN, AbstractWorkStatusStore.State, TOWN> setState
    ) {
    }

    private static Map<String, ProductExtractionWrapper> forRules;

    public static void staticInitialize() {
        ImmutableMap.Builder<String, ProductExtractionWrapper> b = ImmutableMap.builder();
        b.put(SpecialRules.DROP_LOOT_AS_STACK, new ProductExtractionWrapper() {

                    @Override
                    public <TOWN, ITEM> TOWN preProcess(
                            Ctx<TOWN, ITEM> ctx,
                            TOWN ts
                    ) {
                        ITEM item = ctx.lastInsertedIngredients.get();
                        if (item == null) {
                            QT.JOB_LOGGER.error("Inserted ingredient is missing. Resetting state. This is a bug.");
                            return ctx.setState.apply(ts, AbstractWorkStatusStore.State.fresh());
                        }
                        Integer qy = ctx.ingredientQuantityRequired.get();
                        if (qy == null) {
                            throw new IllegalStateException(
                                    "DROP_LOOT_AS_STACK can only be used when previous stage has quantity");
                        }
                        return ctx.addToNearbyChest.apply(ts, ctx.createStackWithQuantity.apply(item, qy));
                    }
                }
        );
        forRules = b.build();
    }

    public static Collection<ProductExtractionWrapper> get(Collection<String> i) {
        ImmutableList.Builder<ProductExtractionWrapper> b = ImmutableList.builder();
        for (String s : i) {
            ProductExtractionWrapper v = forRules.get(s);
            if (v == null) {
                continue;
            }
            b.add(v);
        }
        return b.build();
    }

    public interface ProductExtractionWrapper {
        <TOWN, ITEM> TOWN preProcess(
                Ctx<TOWN, ITEM> ctx,
                TOWN ts
        );
    }
}
