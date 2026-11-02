package ca.bradj.questown.mobs.helperchicken;

/** The helper chicken's lessons, in order. Saved by name, so reordering is safe; renaming is not (an unknown name reads as {@link #FORFEIT}). */
public enum ChickenBeatState {
    WAITING_FOR_STICK,
    WAITING_FOR_WALL_BLOCK,
    WAITING_FOR_DOOR,
    WAITING_FOR_WAND_ON_DOOR,
    WAITING_FOR_ROOM_BUILD,
    WAITING_FOR_CHEST,
    WAITING_FOR_WAND_ON_CAMPFIRE,
    WAITING_FOR_PRESSURE_PLATE,
    WAITING_FOR_FLAG_UI,
    SUNSET_AND_MAP,
    WAITING_FOR_SIGN,
    WAITING_FOR_FREE_FOOD,
    WAITING_FOR_VILLAGER_UI,
    AWAITING_WORLDLY_SEEDS_DELIVERY,
    COMPLETE,
    FORFEIT;

    /** Reads a saved lesson; null or unknown gives {@link #FORFEIT}. */
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
