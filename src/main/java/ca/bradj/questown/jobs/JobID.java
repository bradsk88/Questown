package ca.bradj.questown.jobs;

import ca.bradj.questown.Questown;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Objects;

public record JobID(String rootId, String jobId) {
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

    public static JobID fromTag(CompoundTag v) {
        return new JobID(v.getString("root"), v.getString("jobId"));
    }

    public static Tag toTag(JobID jobID) {
        CompoundTag ct = new CompoundTag();
        ct.putString("root", jobID.rootId);
        ct.putString("jobId", jobID.jobId);
        return ct;
    }

    public static Tag toTag(Collection<JobID> jobIDS) {
        ListTag lt = new ListTag();
        for (JobID jobID : jobIDS) {
            lt.add(JobID.toTag(jobID));
        }
        return lt;
    }

    public static ResourceLocation toRL(JobID jobId) {
        return Questown.ResourceLocation(jobId.rootId + "_qt_" + jobId.jobId);
    }

    public static JobID fromRL(ResourceLocation rl) {
        String[] s = rl.getPath().split("_qt_");
        if (s.length != 2) {
            throw new IllegalArgumentException("Unexpected JobID ResourceLocation format: " + rl);
        }

        return new JobID(s[0], s[1]);
    }

    @Override
    public String toString() {
        return "JobID{" + "rootId='" + rootId + '\'' + ", jobId='" + jobId + '\'' + '}';
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


    public String toNiceString() {
        return String.format("%s:%s", rootId, jobId);
    }

    public boolean sameRoot(JobID other) {
        return rootId.equals(other.rootId);
    }
}
