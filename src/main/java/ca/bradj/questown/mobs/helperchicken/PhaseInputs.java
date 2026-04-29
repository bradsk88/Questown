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
        boolean isNight
) {
}
