package ca.bradj.questown.jobs;

import ca.bradj.questown.integration.minecraft.MCHeldItem;
import ca.bradj.questown.integration.minecraft.MCTownItem;
import ca.bradj.questown.jobs.WorksBehaviour.TownData;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

public class ItemCheckWrappers {

    public static Collection<? extends Function<Predicate<MCHeldItem>, Predicate<MCHeldItem>>> getForHeld(
            WrapperContext ctx, Collection<String> activeSpecialRules
    ) {
        return get(activeSpecialRules).stream().map(
                cwFn -> (Function<Predicate<MCHeldItem>, Predicate<MCHeldItem>>) mcHeldItemPredicate ->
                        cwFn.wrapHandCheck(ctx, mcHeldItemPredicate)
        ).toList();
    }

    public interface CheckWrapper {
        Predicate<MCTownItem> wrapTownCheck(
                WrapperContext ctx,
                Predicate<MCTownItem> check
        );

        Predicate<MCHeldItem> wrapHandCheck(
                WrapperContext ctx,
                Predicate<MCHeldItem> check
        );

    }

    // TODO: Convert to deferred register for extensibility
    private static Map<String, @NotNull CheckWrapper> forRules;

    public static Optional<@NotNull CheckWrapper> get(String rule) {
        ImmutableList<CheckWrapper> rules = get(ImmutableList.of(rule));
        if (rules.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(rules.get(0));
    }

    public static ImmutableList<@NotNull CheckWrapper> get(Collection<String> apply) {
        ImmutableList.Builder<@NotNull CheckWrapper> b = ImmutableList.builder();
        apply.forEach(rule -> {
            ItemCheckWrappers.CheckWrapper wrapper = forRules.get(rule);
            if (wrapper == null) {
                return;
            }
            b.add(wrapper);
        });
        return b.build();
    }

    public record WrapperContext(
            TownData townData,
            Supplier<Integer> capacity,
            Predicate<AmountHeld> quantityMet,
            Supplier<? extends Collection<MCHeldItem>> inventory,
            Supplier<@Nullable Integer> quantityRequired
    ) {
    }

    /**
     * Held items include extra context, like the loot table that was used to
     * acquire them and the biomes from which they were acquired. If that
     * information is not relevant for your check wrapper, use this function to
     * simplify instantiation.
     */
    public static CheckWrapper IgnoringItemOrigin(
            BiFunction<WrapperContext, Predicate<MCTownItem>, Predicate<MCTownItem>> checkWrapper
    ) {
        return new CheckWrapper() {
            @Override
            public Predicate<MCTownItem> wrapTownCheck(
                    WrapperContext ctx,
                    Predicate<MCTownItem> check
            ) {
                return checkWrapper.apply(ctx, check);
            }

            @Override
            public Predicate<MCHeldItem> wrapHandCheck(
                    WrapperContext ctx,
                    Predicate<MCHeldItem> check
            ) {
                Predicate<MCTownItem> originalCheckT = tI -> check.test(MCHeldItem.fromTown(tI));
                return i -> {
                    MCTownItem ti = i.get();
                    return wrapTownCheck(ctx, originalCheckT).test(ti);
                };
            }
        };
    }

    ;

    public static void staticInitialize() {
        ImmutableMap.Builder<String, CheckWrapper> b = ImmutableMap.builder();
        b.put(
                SpecialRules.INGREDIENT_ANY_VALID_WORK_OUTPUT,
                IgnoringItemOrigin((WrapperContext ctx, Predicate<MCTownItem> originalCheck) -> {
                    Predicate<MCTownItem> isAnyWorkResult = item -> Works.isWorkResult(ctx.townData(), item);
                    return item -> JobsClean.shouldTakeItem(
                            ctx.capacity().get(),
                            ImmutableList.of((AmountHeld held, MCTownItem itum) -> {
                                if (ctx.quantityMet().test(held)) {
                                    return false;
                                }
                                return isAnyWorkResult.test(itum) || originalCheck.test(itum);
                            }),
                            ctx.inventory().get(),
                            item
                    );
                })
        );
        b.put(
                SpecialRules.TAKE_ONLY_LESS_THAN_QUANTITY,
                IgnoringItemOrigin((WrapperContext ctx, Predicate<MCTownItem> originalCheck) -> {
                    Integer requiredQuantity = ctx.quantityRequired.get();
                    if (requiredQuantity == null) {
                        return originalCheck;
                    }
                    return item -> {
                        if (item.quantity() >= requiredQuantity) {
                            return false;
                        }
                        return originalCheck.test(item);
                    };
                })
        );
        forRules = b.build();
    }
}
