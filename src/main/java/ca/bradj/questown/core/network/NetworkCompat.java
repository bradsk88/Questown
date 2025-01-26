package ca.bradj.questown.core.network;

import ca.bradj.questown.Questown;
import ca.bradj.questown.jobs.JobID;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

public class NetworkCompat {


    public static ResourceLocation toRL(JobID id) {
        return Questown.ResourceLocation("jobs/%s/%s", id.rootId(), id.jobId());
    }

    public static JobID fromRL(ResourceLocation resourceLocation) {
        if (!resourceLocation.toString().startsWith("questown:jobs/")) {
            throw new IllegalArgumentException("Invalid ResourceLocation for JobID: " + resourceLocation);
        }
        String[] parts = resourceLocation.toString().split("/");
        return new JobID(parts[1], parts[2]);
    }

    public static void toNetwork(
            FriendlyByteBuf buf,
            JobID jobID
    ) {
        buf.writeUtf(jobID.rootId());
        buf.writeUtf(jobID.jobId());
    }

    public static JobID fromNetworkJobID(FriendlyByteBuf buf) {
        String rootId = buf.readUtf();
        String jobId = buf.readUtf();
        return new JobID(rootId, jobId);
    }

    public static @Nullable JobID fromNetworkJobIDNullable(FriendlyByteBuf buffer) {
        String parent = buffer.readUtf();
        JobID p = null;
        if (!parent.equals("null")) {
            p = fromRL(new ResourceLocation(parent));
        }
        return p;
    }

    public static void toNetworkNullable(
            FriendlyByteBuf buffer,
            @Nullable JobID nullableJobId
    ) {
        if (nullableJobId == null) {
            buffer.writeUtf("null");
        } else {
            buffer.writeUtf(toRL(nullableJobId).toString());
        }
    }
}
