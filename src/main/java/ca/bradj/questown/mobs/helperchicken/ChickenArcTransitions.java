package ca.bradj.questown.mobs.helperchicken;

/**
 * Pure-function state machine for the helper-chicken onboarding arc (U4).
 *
 * <p>Given the current {@link ChickenBeatState} and an {@link Observed} snapshot
 * of world conditions, {@link #advance(ChickenBeatState, Observed)} returns the
 * next state. Callers are responsible for reading the snapshot from the world
 * and writing the result back to the flag BE — this class does no I/O.
 *
 * <p>Out-of-order acceptance (R7b): a later beat's condition being satisfied
 * does not let the arc skip earlier beats. Instead, the advance function walks
 * states in enum-ordinal order and advances past every state whose condition
 * is satisfied. In practice this means "if the player already placed the chest
 * before placing the sign, the chest beat auto-completes the tick after the
 * sign beat does". The chicken's animation target always tracks the
 * lowest-numbered incomplete state, which is whatever this function returns.
 */
public final class ChickenArcTransitions {

    /**
     * Snapshot of all observable world conditions consumed by the state machine.
     *
     * <p>Each flag is an independent observation: the machine does not care
     * whether the condition was just met or has been true for a while. State
     * transitions are idempotent under repeated observations of the same
     * snapshot — calling {@code advance} twice with the same inputs returns
     * the same output.
     *
     * <p>Callers populate this record via {@link ChickenArcConditions}.
     */
    public record Observed(
            boolean playerHasWand,
            boolean campfireLit,
            boolean sleepHappenedToday,
            boolean wallBlockPlaced,
            boolean doorPlaced,
            boolean roomRegistered,
            boolean signPlacedAsJobBoard,
            boolean chestPlaced,
            boolean welcomeMatPlaced,
            boolean playerOpenedVillagerUi,
            boolean playerOpenedFlagUi,
            boolean worldlySeedsInAnyContainer,
            boolean playerGaveSeedsToChicken
    ) {
    }

    private ChickenArcTransitions() {
    }

    /**
     * Advances the beat state by consuming every observed condition that
     * applies to the current state or any state between it and the next
     * unsatisfied one. Returns the new state, which may equal the input.
     *
     * <p>{@link ChickenBeatState#COMPLETE} and {@link ChickenBeatState#FORFEIT}
     * are terminal — neither changes regardless of observations.
     */
    public static ChickenBeatState advance(
            ChickenBeatState current,
            Observed o
    ) {
        if (current == ChickenBeatState.COMPLETE || current == ChickenBeatState.FORFEIT) {
            return current;
        }
        ChickenBeatState state = current;
        while (isSatisfied(state, o)) {
            ChickenBeatState next = nextOrdinal(state);
            if (next == state) {
                return state;
            }
            state = next;
        }
        return state;
    }

    private static boolean isSatisfied(
            ChickenBeatState state,
            Observed o
    ) {
        return switch (state) {
            case WAITING_FOR_STICK -> o.playerHasWand();
            case WAITING_FOR_WAND_ON_CAMPFIRE -> o.campfireLit();
            case SUNSET_AND_MAP -> o.sleepHappenedToday();
            case WAITING_FOR_WALL_BLOCK -> o.wallBlockPlaced();
            case WAITING_FOR_DOOR -> o.doorPlaced();
            case WAITING_FOR_WAND_ON_DOOR -> o.roomRegistered();
            case WAITING_FOR_SIGN -> o.signPlacedAsJobBoard();
            case WAITING_FOR_CHEST -> o.chestPlaced();
            case WAITING_FOR_PRESSURE_PLATE -> o.welcomeMatPlaced();
            case WAITING_FOR_VILLAGER_UI -> o.playerOpenedVillagerUi();
            case WAITING_FOR_FLAG_UI -> o.playerOpenedFlagUi();
            case AWAITING_WORLDLY_SEEDS_DELIVERY -> o.playerGaveSeedsToChicken();
            case COMPLETE, FORFEIT -> false;
        };
    }

    private static ChickenBeatState nextOrdinal(ChickenBeatState s) {
        ChickenBeatState[] all = ChickenBeatState.values();
        int i = s.ordinal();
        if (i + 1 >= all.length) {
            return s;
        }
        ChickenBeatState next = all[i + 1];
        // FORFEIT is terminal-by-admin-command; never reach it via normal advance.
        return next == ChickenBeatState.FORFEIT ? s : next;
    }
}
