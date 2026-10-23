package ca.bradj.questown.gui;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * How a villager's unmet item reaches its status tooltip and needs row (r9 findings #4, dev
 * ruling 2026-10-23). While the villager is not working (seeking work, downtime, looking for
 * supplies), the item the town knows it is blocked on replaces the generic second status line
 * and the needs row; while working, both stay as the job describes them. Generic so the rule is
 * unit-testable without Minecraft text types.
 */
public final class StatusNeedLine {

    private StatusNeedLine() {
    }

    public static <T> List<T> lines(List<T> base, @Nullable T needLine, boolean working) {
        if (needLine == null || working) {
            return base;
        }
        List<T> out = new ArrayList<>();
        if (!base.isEmpty()) {
            out.add(base.get(0));
        }
        out.add(needLine);
        return out;
    }

    public static <T> List<T> neededItems(List<T> jobWants, @Nullable T unmetItem, boolean working) {
        if (unmetItem == null || working) {
            return jobWants;
        }
        return List.of(unmetItem);
    }
}
