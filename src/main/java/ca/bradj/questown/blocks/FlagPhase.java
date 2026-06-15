package ca.bradj.questown.blocks;

import net.minecraft.util.StringRepresentable;

/**
 * The lifecycle phase of a {@link TownFlagBlock}, tracked as a blockstate property so it
 * survives chunk unload and can drive client-side presentation (e.g. shutdown particles).
 *
 * <p>Distinct from {@link TownFlagBlock#INACTIVE}, which is the transient player-absence flag
 * (set true when no players are in the tick radius, reset each tick). {@code FlagPhase} is the
 * authoritative relocation lifecycle: an {@link #ACTIVE} town ticks normally; a
 * {@link #SHUTTING_DOWN} town is running the shutdown ritual; a {@link #DORMANT} town is parked
 * in the world holding its data while a relocation deed is in transit.
 */
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

    /**
     * Whether a flag in this phase drives the normal town tick (jobs, quests, villagers).
     * Only an {@link #ACTIVE} town ticks; shutting-down and dormant towns are quiesced.
     */
    public boolean ticksTown() {
        return this == ACTIVE;
    }
}
