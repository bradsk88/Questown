package ca.bradj.questown.mobs.helperchicken;

import java.util.Set;

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
            boolean roomBuilt,
            boolean signPlacedAsJobBoard,
            boolean chestPlaced,
            boolean welcomeMatPlaced,
            boolean bonusFoodGiven,
            boolean playerOpenedVillagerUi,
            boolean playerOpenedFlagUi,
            boolean worldlySeedsInAnyContainer,
            boolean playerGaveSeedsToChicken,
            boolean handPlaced
    ) {
    }

    private ChickenArcTransitions() {
    }

    /**
     * Beats the player may skip. An optional beat auto-advances (see
     * {@link #shouldAdvance}) when the NEXT beat's condition is met, so it never
     * stalls the arc for a player who does not perform the optional action.
     */
    private static final Set<ChickenBeatState> OPTIONAL =
            Set.of(ChickenBeatState.WAITING_FOR_FREE_FOOD);

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
        while (shouldAdvance(state, o)) {
            ChickenBeatState next = nextOrdinal(state);
            if (next == state) {
                return state;
            }
            state = next;
        }
        return state;
    }

    /**
     * A beat advances when its own condition is met. An optional beat also
     * advances (is skipped over) when the NEXT beat's condition is met, so a
     * player who skips the optional action still progresses the arc instead of
     * stalling at the optional beat.
     */
    private static boolean shouldAdvance(
            ChickenBeatState state,
            Observed o
    ) {
        if (isSatisfied(state, o)) {
            return true;
        }
        return OPTIONAL.contains(state) && isSatisfied(nextOrdinal(state), o);
    }

    public static boolean isSatisfied(
            ChickenBeatState state,
            Observed o
    ) {
        return switch (state) {
            case WAITING_FOR_STICK -> o.playerHasWand();
            case WAITING_FOR_WAND_ON_CAMPFIRE -> o.campfireLit();
            case SUNSET_AND_MAP -> o.sleepHappenedToday();
            // The fill-the-hole beat assumes the empty_town structure's pre-dug
            // hole. A hand-placed flag has no such hole, so wallBlockPlaced can
            // never be true and the beat would stall the arc forever. Skip it when
            // the flag is hand-placed; the room lesson is still carried by the
            // door beats that follow (a registered door with no room bubbles a
            // "no room detected" hint that nudges the player to build one).
            case WAITING_FOR_WALL_BLOCK -> o.wallBlockPlaced() || o.handPlaced();
            case WAITING_FOR_DOOR -> o.doorPlaced();
            case WAITING_FOR_WAND_ON_DOOR -> o.roomRegistered();
            case WAITING_FOR_ROOM_BUILD -> o.roomBuilt();
            case WAITING_FOR_SIGN -> o.signPlacedAsJobBoard();
            case WAITING_FOR_CHEST -> o.chestPlaced();
            case WAITING_FOR_PRESSURE_PLATE -> o.welcomeMatPlaced();
            case WAITING_FOR_FREE_FOOD -> o.bonusFoodGiven();
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
