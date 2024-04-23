package ca.bradj.questown.jobs;

import java.util.Collection;
import java.util.function.BiPredicate;
import java.util.function.Predicate;

public class SpecialRuleIngredientAnyValidWorkOutput {
    public static <ITEM> Predicate<ITEM> apply(
            Collection<String> rules,
            Predicate<ITEM> originalCheck,
            Predicate<ITEM> isWorkResult
    ) {
        if (!rules.contains(SpecialRules.INGREDIENT_ANY_VALID_WORK_OUTPUT)) {
            return originalCheck;
        }
        return z -> isWorkResult.test(z) || originalCheck.test(z);
    }

    public static <ITEM> BiPredicate<Integer, ITEM> apply2(
            Collection<String> rules,
            BiPredicate<Integer, ITEM> originalCheck,
            BiPredicate<Integer, ITEM> isWorkResult
    ) {
        if (!rules.contains(SpecialRules.INGREDIENT_ANY_VALID_WORK_OUTPUT)) {
            return originalCheck;
        }
        return (i, z) -> isWorkResult.test(i, z) || originalCheck.test(i, z);
    }
}
