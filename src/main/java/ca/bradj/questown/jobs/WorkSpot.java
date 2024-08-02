package ca.bradj.questown.jobs;

import org.jetbrains.annotations.NotNull;

import java.util.Objects;

public record WorkSpot<A, P>(WorkPosition<P> workPos, A action, int score) {
    public WorkSpot(
            @NotNull P position,
            @NotNull A action,
            int score,
            P interactionSpot
    ) {
        this(new WorkPosition<>(position, interactionSpot), action, score);
    }

    @Override
    public String toString() {
        return "WorkSpot{" +
                "position=" + workPos +
                ", action=" + action +
                ", score=" + score +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        WorkSpot<?, ?> workSpot = (WorkSpot<?, ?>) o;
        return score == workSpot.score && Objects.equals(action, workSpot.action) && Objects.equals(workPos, workSpot.workPos);
    }

    @Override
    public int hashCode() {
        return Objects.hash(workPos, action, score);
    }
}
