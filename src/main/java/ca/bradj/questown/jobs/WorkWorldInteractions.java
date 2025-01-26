package ca.bradj.questown.jobs;

import ca.bradj.questown.integration.minecraft.MCHeldItem;

public record WorkWorldInteractions(
        int actionDuration,
        ResultGenerator<MCHeldItem> resultGenerator
) {
}
