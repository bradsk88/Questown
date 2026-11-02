package ca.bradj.questown.blocks;

import net.minecraft.util.StringRepresentable;

/** Where the town flag is in its life: active, shutting down, or dormant while its deed is carried elsewhere. */
public enum FlagPhase implements StringRepresentable {
    ACTIVE("active"),
    SHUTTING_DOWN("shutting_down"),
    DORMANT("dormant");

    private final String serializedName;

    FlagPhase(String serializedName) {
        this.serializedName = serializedName;
    }

    @Override
    public String getSerializedName() {
        return serializedName;
    }

    /** Only an active town runs its jobs, quests and villagers. */
    public boolean ticksTown() {
        return this == ACTIVE;
    }
}
