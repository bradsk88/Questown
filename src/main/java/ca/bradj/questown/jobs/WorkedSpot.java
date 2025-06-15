package ca.bradj.questown.jobs;

import java.util.Objects;

public final class WorkedSpot<POS> {
    private final POS workPosition;
    private final Integer state;
    private final Integer beforeState;

    public WorkedSpot(
            POS workPosition,
            Integer state
    ) {
        this(workPosition, state, null);
    }

    private WorkedSpot(
            POS workPosition,
            Integer state,
            Integer beforeState
    ) {

        this.workPosition = workPosition;
        this.state = state;
        this.beforeState = beforeState;
    }

    public WorkedSpot<POS> withBefore(Integer state) {
        return new WorkedSpot<>(workPosition, state(), state);
    }

    public POS workPosition() {
        return workPosition;
    }

    public Integer state() {
        return state;
    }

    @Override
    public String toString() {
        return "WorkedSpot{" +
                "workPosition=" + workPosition +
                ", state=" + state +
                ", beforeState=" + beforeState +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        WorkedSpot<?> that = (WorkedSpot<?>) o;
        return Objects.equals(workPosition, that.workPosition) && Objects.equals(
                state,
                that.state
        ) && Objects.equals(beforeState, that.beforeState);
    }

    @Override
    public int hashCode() {
        return Objects.hash(workPosition, state, beforeState);
    }

    public int previousState() {
        return beforeState;
    }
}
