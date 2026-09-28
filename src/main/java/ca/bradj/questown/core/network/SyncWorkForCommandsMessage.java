package ca.bradj.questown.core.network;

import ca.bradj.questown.gui.ClientAccess;
import ca.bradj.questown.jobs.JobID;
import com.google.common.collect.ImmutableList;
import ca.bradj.questown.loader.Channel;
import net.minecraft.network.RegistryFriendlyByteBuf;


public record SyncWorkForCommandsMessage(
        ImmutableList<JobID> jobs
) implements ClientRunnable {

    public static void encode(
            SyncWorkForCommandsMessage msg,
            RegistryFriendlyByteBuf buffer
    ) {
        buffer.writeCollection(msg.jobs, NetworkCompat::toNetwork);
    }

    public static SyncWorkForCommandsMessage decode(RegistryFriendlyByteBuf buffer) {
        return new SyncWorkForCommandsMessage(
                ImmutableList.copyOf(buffer.readList(NetworkCompat::fromNetworkJobID))
        );
    }

    public void handle(
            Channel.Context ctx
    ) {
        ToClientMessage.handle(ctx, this);
    }

    @Override
    public void runOnClient() {
        ClientAccess.syncJobsForCommands(jobs);
    }
}
