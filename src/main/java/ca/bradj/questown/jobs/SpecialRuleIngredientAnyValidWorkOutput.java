package ca.bradj.questown.jobs;

import java.util.Collection;
import java.util.function.Predicate;

public class SpecialRuleIngredientAnyValidWorkOutput {
    public static <ITEM> Predicate<ITEM> apply(
            Collection<String> rules,
            Predicate<ITEM> oPred,
            Predicate<ITEM> isWorkResult
    ) {
        if (!rules.contains(SpecialRules.INGREDIENT_ANY_VALID_WORK_OUTPUT)) {
            return oPred;
        }
        return z -> isWorkResult.test(z) || oPred.test(z);
    }
}
