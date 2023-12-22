package ca.bradj.questown.jobs;

import org.jetbrains.annotations.NotNull;

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
}
