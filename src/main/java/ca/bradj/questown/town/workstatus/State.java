package ca.bradj.questown.town.workstatus;

import java.util.Objects;
import java.util.function.Function;

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

    /**
     * The work state of a job block for room-set / supply-grab purposes.
     *
     * <p>A null lookup means the block's per-owner work status is not yet seeded in
     * <em>this</em> leaver's ledger (e.g. a fresh welcome mat it has not touched) - it does NOT mean
     * the job can't happen there. Treating an unseeded block as "missing" (skip it) leaves the room set
     * empty and stalls the collection loop: the leaver sits in COLLECTING_SUPPLIES and is rejected with
     * {@code shouldTakeItem} REJECT {@code needed-empty}. So an unseeded block is treated as
     * FRESH in state 0, the same as the gate path already does ({@code JobsClean.rooms}).
     *
     * @param getJobBlockState the per-owner lookup (may return null for an unseeded block)
     * @param pos              the job block position
     * @param <POS>            position type
     * @return the block's state, or a fresh state-0 when it is not seeded
     */
    public static <POS> State freshIfNull(Function<POS, State> getJobBlockState, POS pos) {
        State s = getJobBlockState.apply(pos);
        return (s == null) ? freshAtState(0) : s;
    }

    /**
     * Recognition of a welcome-mat block (a leaver work spot) by its registry ID.
     *
     * <p>A welcome mat is a leaver work spot by definition, so the decision "does this block
     * need to be seeded into a leaver's per-owner work-status ledger" must not depend on whether
     * some registered job is currently claiming an interest in the block. Conditioning it on
     * {@code ServerJobsRegistry.shouldInitializeWithState} (which carries a static
     * false-cache that outlives late job registration) left ~half of the leavers' ledgers
     * without the mat's state-0 — the NO_FORWARD_PROGRESS soft-lock
     * (.scratch/cook-soft-lock/STATE.md, Round 4, 2026-10-07).
     */
    public static boolean isWelcomeMat(String blockId) {
        return blockId != null && blockId.contains("welcome_mat");
    }

    /**
     * The initial state a welcome mat holds in a leaver's per-owner ledger: state 0 —
     * "needs the leaver's supplies". Each leaver takes their own supplies out through the mat,
     * so this is seeded unconditionally for every leaver: the ledger must always be able to say
     * "for me, the mat is state 0". Never null.
     */
    public static State welcomeMatSeedState() {
        return fresh();
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
        return (int) Math.ceil(0.1f * workLeft);
    }

    public boolean hasWorkLeft() {
        return workLeft > 0.0;
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
