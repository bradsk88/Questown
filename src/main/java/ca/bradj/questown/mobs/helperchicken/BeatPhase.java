package ca.bradj.questown.mobs.helperchicken;

/** A step within a helper-chicken lesson, picking its bubble and hint. Most lessons have one, {@link #DEFAULT}. */
public enum BeatPhase {
    DEFAULT,
    NEED_TO_FETCH,
    READY_TO_USE,
    READY_TO_PLACE,
    PREPARING,
    AWAITING_NIGHT,
    /** The seeds lesson before the gatherer has left town: a neutral hint instead of "waiting for the villager to return". */
    AWAITING_GATHERER
}
