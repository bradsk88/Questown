package ca.bradj.questown.core.network;

import ca.bradj.questown.gui.ClientAccess;
import ca.bradj.questown.jobs.JobID;
import ca.bradj.questown.jobs.Jobs;
import ca.bradj.questown.jobs.production.ProductionStatus;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public record SyncStatusArtMessage(JobID id, ProductionStatus status, ResourceLocation artID) implements
        ClientRunnable {

    public static void encode(
            SyncStatusArtMessage msg,
            FriendlyByteBuf buffer
    ) {
        Jobs.writeIdToNetwork(buffer, msg.id());
        buffer.writeUtf(msg.status().name());
        buffer.writeResourceLocation(msg.artID());
    }

    public static SyncStatusArtMessage decode(FriendlyByteBuf buffer) {
        return new SyncStatusArtMessage(
                Jobs.getIdFromNetwork(buffer),
                ProductionStatus.fromNumber(buffer.readUtf()),
                buffer.readResourceLocation()
        );
    }

    public void handle(
            Supplier<NetworkEvent.Context> ctx
    ) {
        ToClientMessage.handle(ctx, this);
    }

    @Override
    public void runOnClient() {
        ClientAccess.storeArtOverride(id, status, artID);
    }
}
