package ca.bradj.questown.town.workstatus;

import ca.bradj.questown.town.AbstractWorkStatusStore;

import java.util.Objects;

public class State {
    private final int processingState;
    private final int ingredientCount;

    // IMPORTANT: This value should only be modified by setWorkLeft and
    // internalSetWorkLeft so that the 10x scaling is preserved.
    private final int workLeft;

    private State(
            int processingState,
            int ingredientCounts,
            int workLeft
    ) {
        this.processingState = processingState;
        this.ingredientCount = ingredientCounts;
        this.workLeft = workLeft;
    }

    public static State fresh() {
        return new State(0, 0, 0);
    }

    public static State freshAtState(int s) {
        return new State(s, 0, 0);
    }

    public State setProcessing(int s) {
        return new State(s, ingredientCount, workLeft);
    }

    public State setWorkLeft(int newVal) {
        return new State(processingState, ingredientCount, newVal * 10);
    }

    private State internalSetWorkLeft(int newVal) {
        return new State(processingState, ingredientCount, newVal);
    }

    public State setCount(int count) {
        return new State(processingState, count, workLeft);
    }

    @Override
    public String toString() {
        return "State{" +
                "processingState=" + processingState +
                ", ingredientCount=" + ingredientCount +
                ", workLeft=" + (0.1f * workLeft) +
                '}';
    }

    public String toShortString() {
        return "[" +
                "state=" + processingState +
                ", ingCount=" + ingredientCount +
                ", workLeft=" + (0.1f * workLeft) +
                ']';
    }

    public State incrProcessing() {
        return setProcessing(processingState + 1);
    }

    public State incrIngredientCount() {
        return setCount(ingredientCount + 1);
    }

    public State decrWork(
            int amountOf10
    ) {
        if (amountOf10 > 10 || amountOf10 < 1) {
            throw new IllegalArgumentException("Only 1-10 are allowed (got: " + amountOf10 + ")");
        }
        return internalSetWorkLeft(Math.max(workLeft - amountOf10, 0));
    }

    public boolean isFresh() {
        return fresh().equals(this);
    }

    public int processingState() {
        return processingState;
    }

    public int workLeft() {
        return (int) (0.1f * workLeft);
    }

    public int ingredientCount() {
        return ingredientCount;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        State state = (State) o;
        return processingState == state.processingState && ingredientCount == state.ingredientCount && workLeft == state.workLeft;
    }

    @Override
    public int hashCode() {
        return Objects.hash(processingState, ingredientCount, workLeft);
    }
}
