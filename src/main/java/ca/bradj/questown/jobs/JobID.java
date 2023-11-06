package ca.bradj.questown.jobs;

import java.util.Objects;

public record JobID(
        String rootId,
        String jobId
) {
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
}
