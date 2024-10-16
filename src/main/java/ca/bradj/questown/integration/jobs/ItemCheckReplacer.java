package ca.bradj.questown.integration.jobs;

import ca.bradj.questown.integration.minecraft.MCHeldItem;
import ca.bradj.questown.logic.IPredicateCollection;
import ca.bradj.questown.logic.PredicateCollection;
import com.google.common.collect.ImmutableMap;

import java.util.Collection;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

public class ItemCheckReplacer<ITEM> {
    @SuppressWarnings("rawtypes")
    private static final ItemCheckReplacer DO_NOT_REPLACE = new ItemCheckReplacer<>(null) {
        @Override
        public void replace(Function<ItemCheck<Object>, ItemCheck<Object>> replace) {
        }
    };
    private ItemCheck<ITEM> inner;

    public ItemCheckReplacer(PredicateCollection<ITEM, ITEM> check) {
        this.inner = new ItemCheck<>() {
            @Override
            public boolean isEmpty(Collection<MCHeldItem> heldItems) {
                return check.isEmpty();
            }

            @Override
            public boolean test(
                    Collection<MCHeldItem> heldItems,
                    ITEM item
            ) {
                return check.test(item);
            }
        };
    }

    public static <ITEM> ItemCheckReplacer<ITEM> doNotReplace() {
        //noinspection unchecked
        return DO_NOT_REPLACE;
    }

    public static <ITEM> Map<Integer, PredicateCollection<ITEM, ITEM>> withItems(
            Map<Integer, ItemCheckReplacer<ITEM>> ingr,
            Supplier<? extends Collection<MCHeldItem>> items
    ) {
        ImmutableMap.Builder<Integer, PredicateCollection<ITEM, ITEM>> b = ImmutableMap.builder();
        ingr.forEach((state, replacer) -> b.put(state, replacer.withItems(items)));
        return b.build();
    }

    private PredicateCollection<ITEM, ITEM> withItems(Supplier<? extends Collection<MCHeldItem>> items) {
        return PredicateCollection.wrap(new IPredicateCollection<ITEM>() {
            @Override
            public boolean isEmpty() {
                return inner.isEmpty(items.get());
            }

            @Override
            public boolean test(ITEM item) {
                return inner.test(items.get(), item);
            }
        }, IPredicateCollection::isEmpty, Predicate::test, "wrapped with items supplier");
    }

    public void replace(Function<ItemCheck<ITEM>, ItemCheck<ITEM>> replace) {
        this.inner = replace.apply(inner);
    }
}
