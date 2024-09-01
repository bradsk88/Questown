package ca.bradj.questown.integration.jobs;

import ca.bradj.questown.jobs.JobID;
import net.minecraft.world.entity.Pose;

import java.util.function.Consumer;

public record BeforeMoveToNextStateEvent(
        Consumer<Pose> requestPose,
        Consumer<JobID> requestJobChange
) {
}
