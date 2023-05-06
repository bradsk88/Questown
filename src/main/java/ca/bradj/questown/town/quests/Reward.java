package ca.bradj.questown.town.quests;

import ca.bradj.questown.Questown;

public abstract class Reward {

    private final RewardApplier applier;
    private boolean applied;

    public interface RewardApplier {
        void apply();
    }

    public Reward(
            RewardApplier applier
    ) {
        this.applier = applier;
    }

    void claim() {
        if (this.applied) {
            Questown.LOGGER.error("Refusing to apply reward more than once: {}", this.getName());
            return;
        }
        this.applier.apply();
        this.applied = true;
    }

    protected abstract String getName();

}
