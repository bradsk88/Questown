package ca.bradj.questown.integration.jobs;

import ca.bradj.questown.jobs.declarative.WithReason;
import net.minecraft.core.BlockPos;

import java.util.function.Consumer;
import java.util.function.Function;

public record BeforeFindJobSiteEvent(
        Function<String, String> getUnsafeDataFromVillager,
        Consumer<WithReason<BlockPos>> applyWorkspotOverride
) {
}

