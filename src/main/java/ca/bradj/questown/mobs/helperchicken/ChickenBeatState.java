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
 * under {@code chicken-beat-state}; the persisted value is the name, parsed
 * back by name ({@link #fromNameSafe}), so reordering the enum (not renaming)
 * does not break a saved arc. The order was re-sequenced for the
 * storeroom-first arc (2026-09-09 plan): the storeroom is built and the
 * chicken drops its chest into it before the campfire sleep, and the job
 * board moves to a second room after the sleep. Renaming a value is still
 * unsafe — a saved name would read back as {@link #FORFEIT}.
 */
public enum ChickenBeatState {
    WAITING_FOR_STICK,
    WAITING_FOR_WALL_BLOCK,
    WAITING_FOR_DOOR,
    WAITING_FOR_WAND_ON_DOOR,
    WAITING_FOR_CHEST,
    WAITING_FOR_WAND_ON_CAMPFIRE,
    SUNSET_AND_MAP,
    WAITING_FOR_SIGN,
    WAITING_FOR_PRESSURE_PLATE,
    WAITING_FOR_FREE_FOOD,
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
