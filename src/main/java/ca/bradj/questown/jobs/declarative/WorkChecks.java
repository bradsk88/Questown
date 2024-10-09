package ca.bradj.questown.jobs.declarative;

import ca.bradj.questown.logic.PredicateCollection;
import org.jetbrains.annotations.Nullable;

public interface WorkChecks<EXTRA, ITEM> {
    @Nullable Integer getWorkForStep(int stepState);

    int getWorkForStep(
            int stepState,
            int orDefault
    );

    @Nullable Integer getTimeForStep(
            EXTRA extra,
            int stepState
    );

    int getTimeForStep(EXTRA extra, int stepState, int orDefault);

    @Nullable PredicateCollection<ITEM, ?> getToolsForStep(Integer curState);

    boolean isWorkRequiredAtStep(int action);
}
