package ca.bradj.questown.jobs;

import ca.bradj.questown.integration.minecraft.MCHeldItem;
import ca.bradj.questown.integration.minecraft.MCTownItem;
import ca.bradj.questown.jobs.WorksBehaviour.TownData;
import ca.bradj.questown.jobs.production.ProductionStatus;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Predicate;
import java.util.function.Supplier;

public class ItemCheckWrappers {

    // TODO: Convert to deferred register for extensibility
    private static Map<String, BiFunction<WrapperContext, Predicate<MCTownItem>, Predicate<MCTownItem>>> forRules;

    public static Optional<BiFunction<WrapperContext, Predicate<MCTownItem>, Predicate<MCTownItem>>> get(String rule) {
        BiFunction<WrapperContext, Predicate<MCTownItem>, Predicate<MCTownItem>> v = forRules.get(rule);
        if (v == null) {
            return Optional.empty();
        }
        return Optional.of(v);
    }

    public record WrapperContext(
            TownData townData,
            Supplier<Integer> capacity,
            Predicate<Integer> quantityMet,
            Supplier<? extends Collection<MCHeldItem>> inventory,
            DeclarativeJob job,
            ProductionStatus jobBlockStatus
    ) {
    }

    public static void staticInitialize() {
        ImmutableMap.Builder<String, BiFunction<WrapperContext, Predicate<MCTownItem>, Predicate<MCTownItem>>> b = ImmutableMap.builder();
        b.put(
                SpecialRules.INGREDIENT_ANY_VALID_WORK_OUTPUT,
                (WrapperContext ctx, Predicate<MCTownItem> originalCheck) -> {
                    Predicate<MCTownItem> isAnyWorkResult = item -> Works.isWorkResult(ctx.townData(), item);
                    return item -> JobsClean.shouldTakeItem(
                            ctx.capacity().get(),
                            ImmutableList.of((Integer held, MCTownItem itum) -> {
                                if (ctx.quantityMet().test(held)) {
                                    return false;
                                }
                                return isAnyWorkResult.test(itum) || originalCheck.test(itum);
                            }),
                            ctx.inventory().get(),
                            item
                    );
                }
        );
        b.put(
                SpecialRules.TAKE_ONLY_LESS_THAN_QUANTITY,
                (WrapperContext ctx, Predicate<MCTownItem> originalCheck) -> {
                    Integer requiredQuantity = ctx.job().getRequiredQuantity(ctx.jobBlockStatus());
                    if (requiredQuantity == null) {
                        return originalCheck;
                    }
                    return item -> {
                        if (item.quantity() >= requiredQuantity) {
                            return false;
                        }
                        return originalCheck.test(item);
                    };
                }
        );
        forRules = b.build();
    }
}
