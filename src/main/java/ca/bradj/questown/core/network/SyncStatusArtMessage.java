package ca.bradj.questown.core.network;

import ca.bradj.questown.gui.ClientAccess;
import ca.bradj.questown.jobs.JobID;
import ca.bradj.questown.jobs.Jobs;
import ca.bradj.questown.jobs.production.ProductionStatus;
import ca.bradj.questown.loader.Channel;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;


public record SyncStatusArtMessage(JobID id, ProductionStatus status, ResourceLocation artID) implements
        ClientRunnable {

    public static void encode(
            SyncStatusArtMessage msg,
            RegistryFriendlyByteBuf buffer
    ) {
        Jobs.writeIdToNetwork(buffer, msg.id());
        buffer.writeUtf(msg.status().name());
        buffer.writeResourceLocation(msg.artID());
    }

    public static SyncStatusArtMessage decode(RegistryFriendlyByteBuf buffer) {
        return new SyncStatusArtMessage(
                Jobs.getIdFromNetwork(buffer),
                ProductionStatus.fromNumber(buffer.readUtf()),
                buffer.readResourceLocation()
        );
    }

    public void handle(
            Channel.Context ctx
    ) {
        ToClientMessage.handle(ctx, this);
    }

    @Override
    public void runOnClient() {
        ClientAccess.storeArtOverride(id, status, artID);
    }
}
