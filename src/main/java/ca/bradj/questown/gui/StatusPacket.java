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
        /**
         * Leaver-return bar: the absolute server tick at which this townie's timed trip ends. 0 = not
         * away on a timed trip (no bar).
         */
        long leaverReturnTick,
        /** Total declared ticks of that trip (the bar's denominator). */
        int leaverReturnTotal,
        /** The current step's work done, 0..1, or -1 for no work bar. */
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

    /** Screens without leaver-return info (e.g. the inventory status row) use this. */
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
        b.writeFloat(statusPacket.workProgress);
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
