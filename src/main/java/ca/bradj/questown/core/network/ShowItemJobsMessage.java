package ca.bradj.questown.core.network;

import ca.bradj.questown.gui.ClientAccess;
import ca.bradj.questown.gui.UIJob;
import com.google.common.collect.ImmutableList;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public record ShowItemJobsMessage(
        ImmutableList<UIJob> jobs
) implements ClientRunnable {

    public static void encode(
            ShowItemJobsMessage msg,
            FriendlyByteBuf buffer
    ) {
        buffer.writeCollection(msg.jobs, UIJob::toNetwork);
    }

    public static ShowItemJobsMessage decode(FriendlyByteBuf buffer) {
        return new ShowItemJobsMessage(ImmutableList.copyOf(buffer.readList(UIJob::fromNetwork)));
    }

    public void handle(
            Supplier<NetworkEvent.Context> ctx
    ) {
        ToClientMessage.handle(ctx, this);
    }

    @Override
    public void runOnClient() {
        ClientAccess.openItemJobs(jobs);
    }
}
