package ca.bradj.questown.mobs.helperchicken;

/**
 * World-state booleans that select a beat's active {@link BeatPhase}.
 *
 * <p>{@code hasItem} is per-beat: derived from
 * {@link ChickenArcConditions#playerHoldsRequiredItem}, so its meaning
 * depends on the current beat's required item. {@code chestSpawned} and
 * {@code isNight} are global facts read from the flag BE and the level.
 */
public record PhaseInputs(
        boolean hasItem,
        boolean chestSpawned,
        boolean isNight,
        boolean hasPressurePlate,
        boolean seedsInContainer
) {
    /**
     * 4-arg convenience for callers that don't care about the Worldly-Seeds
     * phase (every beat except {@code AWAITING_WORLDLY_SEEDS_DELIVERY}).
     * Defaults {@code seedsInContainer} to false.
     */
    public PhaseInputs(boolean hasItem, boolean chestSpawned, boolean isNight, boolean hasPressurePlate) {
        this(hasItem, chestSpawned, isNight, hasPressurePlate, false);
    }

    /**
     * 3-arg convenience for callers that don't care about the pressure-plate
     * or Worldly-Seeds phase. Defaults {@code hasPressurePlate} and
     * {@code seedsInContainer} to false so existing construction sites and
     * tests keep compiling unchanged.
     */
    public PhaseInputs(boolean hasItem, boolean chestSpawned, boolean isNight) {
        this(hasItem, chestSpawned, isNight, false, false);
    }
}
