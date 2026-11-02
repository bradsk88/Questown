package ca.bradj.questown.mobs.helperchicken;

/** What picks a lesson's step. {@code hasItem} means holding the current lesson's item. */
public record PhaseInputs(
        boolean hasItem,
        boolean chestSpawned,
        boolean isNight,
        boolean hasPressurePlate,
        boolean seedsInContainer,
        boolean gathererLeft
) {
    public PhaseInputs(
            boolean hasItem, boolean chestSpawned, boolean isNight,
            boolean hasPressurePlate, boolean seedsInContainer
    ) {
        this(hasItem, chestSpawned, isNight, hasPressurePlate, seedsInContainer, false);
    }

    public PhaseInputs(boolean hasItem, boolean chestSpawned, boolean isNight, boolean hasPressurePlate) {
        this(hasItem, chestSpawned, isNight, hasPressurePlate, false, false);
    }

    public PhaseInputs(boolean hasItem, boolean chestSpawned, boolean isNight) {
        this(hasItem, chestSpawned, isNight, false, false, false);
    }
}
