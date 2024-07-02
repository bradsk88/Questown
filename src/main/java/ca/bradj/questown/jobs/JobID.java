package ca.bradj.questown.jobs;

import ca.bradj.questown.Questown;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

public record JobID(
        String rootId,
        String jobId
) {
    public static @Nullable JobID fromJSON(@Nullable String val) {
        if (val == null) {
            return null;
        }
        String[] split = val.split("/");
        if (split.length != 2) {
            String msg = String.format("Invalid JobID %s. Format should match \"<job_id>/<work_id>\"", val);
            throw new IllegalArgumentException(msg);
        }
        return new JobID(split[0], split[1]);
    }

    @Override
    public String toString() {
        return "JobID{" +
                "rootId='" + rootId + '\'' +
                ", jobId='" + jobId + '\'' +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        JobID jobID = (JobID) o;
        return Objects.equals(rootId, jobID.rootId) && Objects.equals(jobId, jobID.jobId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(rootId, jobId);
    }

    public ResourceLocation id() {
        return Questown.ResourceLocation("jobs/%s/%s", rootId, jobId);
    }

    public static JobID fromRL(ResourceLocation resourceLocation) {
        if (!resourceLocation.toString().startsWith("questown:jobs/")) {
            throw new IllegalArgumentException("Invalid ResourceLocation for JobID: " + resourceLocation);
        }
        String[] parts = resourceLocation.toString().split("/");
        return new JobID(parts[1], parts[2]);
    }

    public String toNiceString() {
        return String.format("%s:%s", rootId, jobId);
    }
}
