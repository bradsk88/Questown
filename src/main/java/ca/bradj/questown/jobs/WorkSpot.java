package ca.bradj.questown.jobs;

import org.jetbrains.annotations.NotNull;

public class WorkSpot<P> {
    public final int score;

    public WorkSpot(
            @NotNull P position,
            @NotNull FarmerJob.FarmerAction action,
            int score
    ) {
        this.position = position;
        this.action = action;
        this.score = score;
    }

    public final P position;
    public final FarmerJob.FarmerAction action;

    @Override
    public String toString() {
        return "WorkSpot{" +
                "position=" + position +
                ", action=" + action +
                ", score=" + score +
                '}';
    }

    public int getScore() {
        return score;
    }
}
