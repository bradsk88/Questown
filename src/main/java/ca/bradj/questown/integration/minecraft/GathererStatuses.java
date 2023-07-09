package ca.bradj.questown.integration.minecraft;

import ca.bradj.questown.jobs.GathererJournal;
import net.minecraft.util.StringRepresentable;
import org.jetbrains.annotations.NotNull;

public enum GathererStatuses implements StringRepresentable {
    INVALID("invalid"),
    IDLE("idle"),
    NO_SPACE("no_space"),
    NO_FOOD("no_food"),
    STAYING("staying"),
    GATHERING("gathering"),
    GATHERING_HUNGRY("gathering_hungry"),
    GATHERING_EATING("gathering_eating"),
    RETURNING("returning"),
    RETURNING_AT_NIGHT("returning_at_night"),
    RETURNED_SUCCESS("returned_success"),
    RETURNED_FAILURE("returned_failure"),
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

    public static GathererStatuses fromQT(GathererJournal.Status status) {
        return switch (status) {
            case UNSET -> throw new IllegalArgumentException(
                    "UNSET is a metavalue, and should never be provided"
            );
            case IDLE -> IDLE;
            case NO_SPACE -> NO_SPACE;
            case NO_FOOD -> NO_FOOD;
            case STAYING -> STAYING;
            case GATHERING -> GATHERING;
            case GATHERING_HUNGRY -> GATHERING_HUNGRY;
            case GATHERING_EATING -> GATHERING_EATING;
            case RETURNING -> RETURNING;
            case RETURNING_AT_NIGHT -> RETURNING_AT_NIGHT;
            case RETURNED_SUCCESS -> RETURNED_SUCCESS;
            case RETURNED_FAILURE -> RETURNED_FAILURE;
            case CAPTURED -> CAPTURED;
            case RELAXING -> RELAXING;
        };
    }

    public GathererJournal.Status toQT() {
        return switch (this) {
            case INVALID -> throw new IllegalArgumentException(
                    "INVALID is a metavalue, and should never be provided"
            );
            case IDLE -> GathererJournal.Status.IDLE;
            case NO_SPACE -> GathererJournal.Status.NO_SPACE;
            case NO_FOOD -> GathererJournal.Status.NO_FOOD;
            case STAYING -> GathererJournal.Status.STAYING;
            case GATHERING -> GathererJournal.Status.GATHERING;
            case GATHERING_HUNGRY -> GathererJournal.Status.GATHERING_HUNGRY;
            case GATHERING_EATING -> GathererJournal.Status.GATHERING_EATING;
            case RETURNING -> GathererJournal.Status.RETURNING;
            case RETURNING_AT_NIGHT -> GathererJournal.Status.RETURNING_AT_NIGHT;
            case RETURNED_SUCCESS -> GathererJournal.Status.RETURNED_SUCCESS;
            case RETURNED_FAILURE -> GathererJournal.Status.RETURNED_FAILURE;
            case CAPTURED -> GathererJournal.Status.CAPTURED;
            case RELAXING -> GathererJournal.Status.RELAXING;
        };
    }
}
