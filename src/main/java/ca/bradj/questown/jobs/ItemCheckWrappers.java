package ca.bradj.questown.jobs;

import ca.bradj.questown.integration.minecraft.MCHeldItem;
import ca.bradj.questown.integration.minecraft.MCTownItem;
import ca.bradj.questown.jobs.WorksBehaviour.TownData;
import ca.bradj.questown.jobs.production.ProductionStatus;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Predicate;
import java.util.function.Supplier;

public class ItemCheckWrappers {

    public interface CheckWrapper extends BiFunction<WrapperContext, Predicate<MCTownItem>, Predicate<MCTownItem>> {
    }

    // TODO: Convert to deferred register for extensibility
    private static Map<String, @NotNull CheckWrapper> forRules;

    public static Optional<@NotNull CheckWrapper> get(String rule) {
        CheckWrapper v = forRules.get(rule);
        if (v == null) {
            return Optional.empty();
        }
        return Optional.of(v);
    }

    public record WrapperContext(
            TownData townData,
            Supplier<Integer> capacity,
            Predicate<AmountHeld> quantityMet,
            Supplier<? extends Collection<MCHeldItem>> inventory,
            DeclarativeJob job,
            ProductionStatus jobBlockStatus
    ) {
    }

    public static void staticInitialize() {
        ImmutableMap.Builder<String, CheckWrapper> b = ImmutableMap.builder();
        b.put(
                SpecialRules.INGREDIENT_ANY_VALID_WORK_OUTPUT,
                (WrapperContext ctx, Predicate<MCTownItem> originalCheck) -> {
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
