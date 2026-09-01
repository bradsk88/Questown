package ca.bradj.questown.mobs.helperchicken;

/**
 * Sub-state within a {@link ChickenBeatState} for presentation purposes.
 *
 * <p>Selected by {@link ChickenArcPresentation#activePhase} from a
 * {@link PhaseInputs}. Each {@code (ChickenBeatState, BeatPhase)} pair maps
 * to a single {@link Presentation} row carrying the bubble icon, hint lang
 * key, and plain lang key. Bubble + hint + plain all read from the same
 * row, so drift between them is impossible by construction.
 *
 * <p>Single-phase beats use {@link #DEFAULT}.
 *
 * <p>Vocabulary: see {@code /CONTEXT.md} and
 * {@code docs/adr/0001-chicken-arc-beat-phase.md}.
 */
public enum BeatPhase {
    DEFAULT,
    NEED_TO_FETCH,
    READY_TO_USE,
    READY_TO_PLACE,
    PREPARING,
    AWAITING_NIGHT,
    /**
     * Seeds-delivery beat, before the gatherer has left town. The chicken is
     * at the gate but the villager hasn't gone out yet, so the "waiting for the
     * villager to return" hint ({@link #DEFAULT}) must not fire. Neutral gate
     * hint instead. Distinct from {@link #DEFAULT}: see {@code CONTEXT.md}
     * "phase across contexts" — this reads the gatherer's jobs-domain phase.
     */
    AWAITING_GATHERER
}
