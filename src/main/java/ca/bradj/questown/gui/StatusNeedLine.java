package ca.bradj.questown.gui;

import ca.bradj.questown.jobs.IStatus;
import ca.bradj.questown.jobs.production.ProductionStatus;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * How a villager's unmet item reaches its status tooltip and needs row (r9 findings #4, dev
 * ruling 2026-10-23). While the villager is not working (seeking work, downtime, looking for
 * supplies), the item the town knows it is blocked on replaces the generic second status line
 * and the needs row; while working or eating ({@link #keepsOwnLines}), both stay as the job
 * describes them. Generic so the rule is
 * unit-testable without Minecraft text types.
 */
public final class StatusNeedLine {

    private StatusNeedLine() {
    }

    /**
     * True when the status keeps its own lines and needs row: while working, and while eating. A
     * diner's job blocker read as one thought with the diner line ("Cook is looking for food. /
     * They need Coal to work.", agent playtest r10 #8, dev 2026-10-23).
     */
    /**
     * Working on production, walking to the job site, or away on a timed trip: a hunter out for the
     * day read "They need Food to work." (dev playtest 2026-10-29).
     */
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
