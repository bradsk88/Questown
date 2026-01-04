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
        ResourceLocation image
) {
    public static void toNetwork(
            FriendlyByteBuf b,
            StatusPacket statusPacket
    ) {
        Jobs.writeIdToNetwork(b, statusPacket.jobId);
        b.writeCollection(statusPacket.texts, FriendlyByteBuf::writeComponent);
        b.writeResourceLocation(statusPacket.image);
    }

    public static StatusPacket fromNetwork(FriendlyByteBuf b) {
        JobID j = Jobs.getIdFromNetwork(b);
        List<Component> textKeys = b.readList(FriendlyByteBuf::readComponent);
        ResourceLocation img = b.readResourceLocation();
        return new StatusPacket(j, ImmutableList.copyOf(textKeys), img);
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
