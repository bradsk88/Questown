package ca.bradj.questown.core.network;

import ca.bradj.questown.gui.ClientAccess;
import ca.bradj.questown.gui.StatusPacket;
import ca.bradj.questown.jobs.production.ProductionStatus;
import ca.bradj.questown.loader.Channel;
import net.minecraft.network.RegistryFriendlyByteBuf;


public record SyncStatusTextMessage(
        ProductionStatus status,
        StatusPacket packet
) implements
        ClientRunnable {

    public static void encode(
            SyncStatusTextMessage msg,
            RegistryFriendlyByteBuf buffer
    ) {
        buffer.writeUtf(msg.status().name());
        StatusPacket.toNetwork(buffer, msg.packet);
    }

    public static SyncStatusTextMessage decode(RegistryFriendlyByteBuf buffer) {
        ProductionStatus ps = ProductionStatus.fromNumber(buffer.readUtf());
        return new SyncStatusTextMessage(ps, StatusPacket.fromNetwork(buffer));
    }

    public void handle(
            Channel.Context ctx
    ) {
        ToClientMessage.handle(ctx, this);
    }

    @Override
    public void runOnClient() {
        ClientAccess.storeTextOverride(status, packet);
    }
}
