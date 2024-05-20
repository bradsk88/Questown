package ca.bradj.questown.jobs;

import ca.bradj.questown.integration.minecraft.MCHeldItem;
import ca.bradj.questown.integration.minecraft.MCTownItem;
import ca.bradj.questown.jobs.WorksBehaviour.TownData;
import ca.bradj.questown.jobs.declarative.WithReason;
import ca.bradj.questown.mc.Util;
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

    public static Collection<? extends Function<NoisyPredicate<MCHeldItem>, NoisyPredicate<MCHeldItem>>> getForHeld(
            WrapperContext ctx, Collection<String> activeSpecialRules
    ) {
        return get(activeSpecialRules).stream().map(
                cwFn -> (Function<NoisyPredicate<MCHeldItem>, NoisyPredicate<MCHeldItem>>) mcHeldItemPredicate ->
                        cwFn.wrapHandCheck(ctx, mcHeldItemPredicate)
        ).toList();
    }

    public interface CheckWrapper {
        NoisyPredicate<MCTownItem> wrapTownCheck(
                WrapperContext ctx,
                NoisyPredicate<MCTownItem> check
        );

        NoisyPredicate<MCHeldItem> wrapHandCheck(
                WrapperContext ctx,
                NoisyPredicate<MCHeldItem> check
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
            BiFunction<WrapperContext, NoisyPredicate<MCTownItem>, NoisyPredicate<MCTownItem>> checkWrapper
    ) {
        return new CheckWrapper() {
            @Override
            public NoisyPredicate<MCTownItem> wrapTownCheck(
                    WrapperContext ctx,
                    NoisyPredicate<MCTownItem> check
            ) {
                return checkWrapper.apply(ctx, check);
            }

            @Override
            public NoisyPredicate<MCHeldItem> wrapHandCheck(
                    WrapperContext ctx,
                    NoisyPredicate<MCHeldItem> check
            ) {
                NoisyPredicate<MCTownItem> originalCheckT = tI -> check.test(MCHeldItem.fromTown(tI));
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
                IgnoringItemOrigin((WrapperContext ctx, NoisyPredicate<MCTownItem> originalCheck) -> {
                    Predicate<MCTownItem> isAnyWorkResult = item -> Works.isWorkResult(ctx.townData(), item);
                    return item -> JobsClean.shouldTakeItem(
                            ctx.capacity().get(),
                            ImmutableList.of((AmountHeld held, MCTownItem itum) -> {
                                if (ctx.quantityMet().test(held)) {
                                    return new WithReason<>(false, "Quantity already met with %d held", held);
                                }
                                if (isAnyWorkResult.test(itum)) {
                                    return new WithReason<>(true, "%s is work result", itum.getShortName());
                                }
                                return originalCheck.test(item).wrap("Stack is less than quantity limit. Item is not work result");
                            }),
                            ctx.inventory().get(),
                            item
                    );
                })
        );
        b.put(
                SpecialRules.TAKE_ONLY_LESS_THAN_QUANTITY,
                IgnoringItemOrigin((WrapperContext ctx, NoisyPredicate<MCTownItem> originalCheck) -> {
                    Integer requiredQuantity = ctx.quantityRequired.get();
                    if (requiredQuantity == null) {
                        return originalCheck;
                    }
                    return item -> {
                        if (item.quantity() >= requiredQuantity) {
                            return new WithReason<>(false, "Item stack is larger than job quantity limit");
                        }
                        return originalCheck.test(item).wrap("Item stack is within spec");
                    };
                })
        );
        forRules = b.build();
    }
}
