package ca.bradj.questown.town.quests;

import ca.bradj.questown.QT;
import org.jetbrains.annotations.NotNull;

public abstract class Reward {

    private boolean applied;

    public interface RewardApplier {
        void apply();
    }

    public Reward() {
    }

    protected void markApplied() {
        this.applied = true;
    }

    void claim() {
        if (this.applied) {
            QT.LOGGER.error("Refusing to apply reward more than once: {}", this.getName());
            return;
        }
        this.getApplier().apply();
        this.applied = true;
    }

    public boolean isApplied() {
        return applied;
    }

    protected abstract String getName();

    protected abstract @NotNull RewardApplier getApplier();

}
