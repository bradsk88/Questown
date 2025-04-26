package ca.bradj.questown.gui.villager.advancements;

import ca.bradj.questown.jobs.JobID;
import org.jetbrains.annotations.Nullable;

public record WidgetContext<X>(
        JobRelationship rel,
        @Nullable JobID parentId,
        JobRelationship.ContextualPosition pos,
        X parentWidget
) {
}
