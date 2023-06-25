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
    RETURNED_SUCCESS("returned_success"),
    RETURNED_FAILURE("returned_failure"),
    CAPTURED("captured");

    private final String name;

    GathererStatuses(String name) {
        this.name = name;
    }

    @Override
    public @NotNull String getSerializedName() {
        return this.name;
    }

    public static GathererStatuses fromQT(GathererJournal.Statuses status) {
        return switch (status) {
            case IDLE -> IDLE;
            case NO_SPACE -> NO_SPACE;
            case NO_FOOD -> NO_FOOD;
            case STAYING -> STAYING;
            case GATHERING -> GATHERING;
            case RETURNED_SUCCESS -> RETURNED_SUCCESS;
            case RETURNED_FAILURE -> RETURNED_FAILURE;
            case CAPTURED -> CAPTURED;
        };
    }
}
