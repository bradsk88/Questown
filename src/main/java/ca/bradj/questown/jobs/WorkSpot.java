package ca.bradj.questown.jobs;

import org.jetbrains.annotations.NotNull;

import java.util.Objects;

public record WorkSpot<A, P>(P position, A action, int score, P interactionSpot) {
    public WorkSpot(
            @NotNull P position,
            @NotNull A action,
            int score,
            P interactionSpot
    ) {
        this.position = position;
        this.action = action;
        this.score = score;
        this.interactionSpot = interactionSpot;
    }

    @Override
    public String toString() {
        return "WorkSpot{" +
                "position=" + position +
                ", action=" + action +
                ", score=" + score +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        WorkSpot<?, ?> workSpot = (WorkSpot<?, ?>) o;
        return score == workSpot.score && Objects.equals(action, workSpot.action) && Objects.equals(position, workSpot.position) && Objects.equals(interactionSpot, workSpot.interactionSpot);
    }

    @Override
    public int hashCode() {
        return Objects.hash(position, action, score, interactionSpot);
    }
}
