package ca.bradj.questown.jobs;

import java.util.Map;
import java.util.function.Predicate;

public interface SupplyChecks<I> {

    Map<Integer, ? extends Predicate<I>> getIngredientsForStep();

    Boolean isIngredientRequiredAtStep(Integer integer);

    Map<Integer, ? extends Predicate<I>> getToolsForStep();

    Boolean isToolRequiredAtStep(Integer integer);

    Map<Integer, Integer> getWorkRequiredAtStep();
}
