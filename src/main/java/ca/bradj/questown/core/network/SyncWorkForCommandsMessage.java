package ca.bradj.questown.core.network;

import ca.bradj.questown.gui.ClientAccess;
import ca.bradj.questown.gui.Ingredients;
import ca.bradj.questown.gui.UIJob;
import ca.bradj.questown.jobs.JobID;
import com.google.common.collect.ImmutableList;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public record SyncWorkForCommandsMessage(
        ImmutableList<JobID> jobs
) implements ClientRunnable {

    public static void encode(
            SyncWorkForCommandsMessage msg,
            FriendlyByteBuf buffer
    ) {
        buffer.writeCollection(msg.jobs, NetworkCompat::toNetwork);
    }

    public static SyncWorkForCommandsMessage decode(FriendlyByteBuf buffer) {
        return new SyncWorkForCommandsMessage(
                ImmutableList.copyOf(buffer.readList(NetworkCompat::fromNetworkJobID))
        );
    }

    public void handle(
            Supplier<NetworkEvent.Context> ctx
    ) {
        ToClientMessage.handle(ctx, this);
    }

    @Override
    public void runOnClient() {
        ClientAccess.syncJobsForCommands(jobs);
    }
}
