package ca.bradj.questown.gui;

import ca.bradj.questown.jobs.IStatus;
import ca.bradj.questown.jobs.production.ProductionStatus;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * How a villager's missing item reaches its status tooltip and needs row: shown while the villager is
 * not working, left out while it works or eats.
 */
public final class StatusNeedLine {

    private StatusNeedLine() {
    }

    /** True while working (at the job, walking to it, or away on a trip) or eating. */
    public static boolean isWorking(@Nullable IStatus<?> status) {
        return status instanceof ProductionStatus ps && (ps.isWorkingOnProduction() || ps.isGoingToJobsite()
                || ProductionStatus.WAITING_FOR_TIMED_STATE.equals(ps));
    }

    public static boolean keepsOwnLines(boolean working, boolean dining) {
        return working || dining;
    }

    public static <T> List<T> lines(List<T> base, @Nullable T needLine, boolean keepOwnLines) {
        if (needLine == null || keepOwnLines) {
            return base;
        }
        List<T> out = new ArrayList<>();
        if (!base.isEmpty()) {
            out.add(base.get(0));
        }
        out.add(needLine);
        return out;
    }

    public static <T> List<T> neededItems(List<T> jobWants, @Nullable T unmetItem, boolean keepOwnLines) {
        if (unmetItem == null || keepOwnLines) {
            return jobWants;
        }
        return List.of(unmetItem);
    }
}
