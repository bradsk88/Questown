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
        boolean hasPressurePlate
) {
    /**
     * 3-arg convenience for callers that don't care about the pressure-plate
     * phase (every beat except {@code WAITING_FOR_PRESSURE_PLATE}). Defaults
     * {@code hasPressurePlate} to false so existing construction sites and
     * tests keep compiling unchanged.
     */
    public PhaseInputs(boolean hasItem, boolean chestSpawned, boolean isNight) {
        this(hasItem, chestSpawned, isNight, false);
    }
}
