package ca.bradj.questown.core.network;

import ca.bradj.questown.gui.ClientAccess;
import ca.bradj.questown.gui.StatusPacket;
import ca.bradj.questown.jobs.production.ProductionStatus;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public record SyncStatusTextMessage(
        ProductionStatus status,
        StatusPacket packet
) implements
        ClientRunnable {

    public static void encode(
            SyncStatusTextMessage msg,
            FriendlyByteBuf buffer
    ) {
        buffer.writeUtf(msg.status().name());
        StatusPacket.toNetwork(buffer, msg.packet);
    }

    public static SyncStatusTextMessage decode(FriendlyByteBuf buffer) {
        ProductionStatus ps = ProductionStatus.fromNumber(buffer.readUtf());
        return new SyncStatusTextMessage(ps, StatusPacket.fromNetwork(buffer));
    }

    public void handle(
            Supplier<NetworkEvent.Context> ctx
    ) {
        ToClientMessage.handle(ctx, this);
    }

    @Override
    public void runOnClient() {
        ClientAccess.storeTextOverride(status, packet);
    }
}
