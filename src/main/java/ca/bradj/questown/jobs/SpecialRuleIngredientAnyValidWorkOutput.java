package ca.bradj.questown.jobs;

import java.util.Collection;
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
}
