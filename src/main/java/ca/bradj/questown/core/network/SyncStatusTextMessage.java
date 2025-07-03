package ca.bradj.questown.core.network;

import ca.bradj.questown.gui.ClientAccess;
import ca.bradj.questown.jobs.JobID;
import ca.bradj.questown.jobs.Jobs;
import ca.bradj.questown.jobs.production.ProductionStatus;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public record SyncStatusTextMessage(JobID id, ProductionStatus status, String text_1, String text_2) implements
        ClientRunnable {

    public static void encode(
            SyncStatusTextMessage msg,
            FriendlyByteBuf buffer
    ) {
        Jobs.writeIdToNetwork(buffer, msg.id());
        buffer.writeUtf(msg.status().name());
        buffer.writeUtf(msg.text_1());
        buffer.writeUtf(msg.text_2());
    }

    public static SyncStatusTextMessage decode(FriendlyByteBuf buffer) {
        return new SyncStatusTextMessage(
                Jobs.getIdFromNetwork(buffer),
                ProductionStatus.fromNumber(buffer.readUtf()),
                buffer.readUtf(),
                buffer.readUtf()
        );
    }

    public void handle(
            Supplier<NetworkEvent.Context> ctx
    ) {
        ToClientMessage.handle(ctx, this);
    }

    @Override
    public void runOnClient() {
        ClientAccess.storeTextOverride(id, status, text_1, text_2);
    }
}
