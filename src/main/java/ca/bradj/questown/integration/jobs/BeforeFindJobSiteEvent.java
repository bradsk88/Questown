package ca.bradj.questown.integration.jobs;

import ca.bradj.questown.jobs.declarative.WithReason;
import net.minecraft.core.BlockPos;

import java.util.function.Consumer;

public record BeforeFindJobSiteEvent(
        UnsafeVillagerData unsafeVillagerData,
        Consumer<WithReason<BlockPos>> applyWorkspotOverride
) {
}

