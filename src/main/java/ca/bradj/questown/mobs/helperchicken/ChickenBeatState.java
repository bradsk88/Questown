package ca.bradj.questown.mobs.helperchicken;

/**
 * Closed enum of beat states for the helper chicken onboarding arc.
 *
 * <p>The ordering is the authoritative linear state machine used by the
 * {@code ChickenArcController} (U6): the controller advances to the
 * lowest-numbered incomplete state. Any state whose observable condition
 * is satisfied is marked complete.
 *
 * <p>These names are persisted as {@code .name()} in the flag BE NBT
 * under {@code chicken-beat-state}. They must NOT be reordered or
 * renamed — persisted saves will break.
 */
public enum ChickenBeatState {
    WAITING_FOR_STICK,
    WAITING_FOR_WAND_ON_CAMPFIRE,
    SUNSET_AND_MAP,
    WAITING_FOR_WALL_BLOCK,
    WAITING_FOR_DOOR,
    WAITING_FOR_WAND_ON_DOOR,
    WAITING_FOR_SIGN,
    WAITING_FOR_CHEST,
    WAITING_FOR_PRESSURE_PLATE,
    WAITING_FOR_VILLAGER_UI,
    WAITING_FOR_FLAG_UI,
    AWAITING_WORLDLY_SEEDS_DELIVERY,
    COMPLETE,
    FORFEIT;

    /**
     * Safe parse that tolerates null or unknown values from persisted NBT.
     * Returns {@link #FORFEIT} rather than throwing — unknown states mean the
     * arc is irrecoverable and we should hide the chicken.
     */
    public static ChickenBeatState fromNameSafe(String s) {
        if (s == null) {
            return FORFEIT;
        }
        try {
            return ChickenBeatState.valueOf(s);
        } catch (IllegalArgumentException e) {
            return FORFEIT;
        }
    }
}
