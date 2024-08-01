package ca.bradj.questown.integration.minecraft;

import net.minecraft.util.StringRepresentable;
import org.jetbrains.annotations.NotNull;

public enum GathererStatuses implements StringRepresentable {
    INVALID("invalid"),
    IDLE("idle"),
    NO_SPACE("no_space"),
    NO_FOOD("no_food"),
    NO_GATE("no_gate"),
    STAYING("staying"),
    GATHERING("gathering"),
    GATHERING_HUNGRY("gathering_hungry"),
    GATHERING_EATING("gathering_eating"),
    RETURNING("returning"),
    RETURNING_AT_NIGHT("returning_at_night"),
    RETURNED_SUCCESS("returned_success"),
    RETURNED_FAILURE("returned_failure"),
    DROPPING_LOOT("dropping_loot"),
    CAPTURED("captured"),
    RELAXING("relaxing");

    private final String name;

    GathererStatuses(String name) {
        this.name = name;
    }

    public static GathererStatuses fromString(String status) {
        switch (status) {
            case ("no_space") -> {
                return NO_SPACE;
            }
            case ("no_food") -> {
                return NO_FOOD;
            }
            case ("no_gate") -> {
                return NO_GATE;
            }
            case ("staying") -> {
                return STAYING;
            }
            case ("gathering") -> {
                return GATHERING;
            }
            case ("returning") -> {
                return RETURNING;
            }
            case ("returned_success") -> {
                return RETURNED_SUCCESS;
            }
            case ("returned_failure") -> {
                return RETURNED_FAILURE;
            }
            case ("captured") -> {
                return CAPTURED;
            }
            case ("relaxing") -> {
                return RELAXING;
            }
        }
        return INVALID;
    }

    @Override
    public @NotNull String getSerializedName() {
        return this.name;
    }
}
