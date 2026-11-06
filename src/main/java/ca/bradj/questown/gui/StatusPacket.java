package ca.bradj.questown.gui;

import ca.bradj.questown.jobs.JobID;
import ca.bradj.questown.jobs.Jobs;
import com.google.common.collect.ImmutableList;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Objects;

public record StatusPacket(
        JobID jobId,
        ImmutableList<Component> texts,
        ResourceLocation image,
        /** The server tick when the villager's trip ends, or 0 when it is not away. */
        long leaverReturnTick,
        /** The trip's total ticks. */
        int leaverReturnTotal,
        float workProgress
) {
    public StatusPacket(
            JobID jobId,
            ImmutableList<Component> texts,
            ResourceLocation image,
            long leaverReturnTick,
            int leaverReturnTotal
    ) {
        this(jobId, texts, image, leaverReturnTick, leaverReturnTotal, -1);
    }
    /** For screens without trip info. */
    public StatusPacket(
            JobID jobId,
            ImmutableList<Component> texts,
            ResourceLocation image
    ) {
        this(jobId, texts, image, 0L, 0);
    }
    public static void toNetwork(
            FriendlyByteBuf b,
            StatusPacket statusPacket
    ) {
        Jobs.writeIdToNetwork(b, statusPacket.jobId);
        b.writeCollection(statusPacket.texts, (buf, c) -> buf.writeUtf(c.getString()));
        b.writeResourceLocation(statusPacket.image);
        b.writeVarLong(statusPacket.leaverReturnTick);
        b.writeVarInt(statusPacket.leaverReturnTotal);
    }

    public static StatusPacket fromNetwork(FriendlyByteBuf b) {
        JobID j = Jobs.getIdFromNetwork(b);
        List<Component> textKeys = b.readList(buf -> net.minecraft.network.chat.Component.literal(buf.readUtf()));
        ResourceLocation img = b.readResourceLocation();
        long leaverReturnTick = b.readVarLong();
        int leaverReturnTotal = b.readVarInt();
        float workProgress = b.readFloat();
        return new StatusPacket(j, ImmutableList.copyOf(textKeys), img, leaverReturnTick, leaverReturnTotal, workProgress);
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        StatusPacket that = (StatusPacket) o;
        return Objects.equals(jobId, that.jobId) && Objects.equals(
                image,
                that.image
        ) && Objects.equals(texts, that.texts);
    }

    @Override
    public int hashCode() {
        return Objects.hash(jobId, texts, image);
    }
}
