package ca.bradj.questown.core.network;

import ca.bradj.questown.gui.ClientAccess;
import ca.bradj.questown.gui.Ingredients;
import ca.bradj.questown.gui.UIJob;
import com.google.common.collect.ImmutableList;
import net.minecraft.core.BlockPos;
import ca.bradj.questown.loader.Channel;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.item.crafting.Ingredient;


public record ShowItemJobsMessage(
        Ingredient requestedItem,
        ImmutableList<UIJob> jobs,
        BlockPos flagPos
) implements ClientRunnable {

    public static void encode(
            ShowItemJobsMessage msg,
            RegistryFriendlyByteBuf buffer
    ) {
        String s = "";
        if (!msg.requestedItem.isEmpty()) {
            s = Ingredients.toString(msg.requestedItem);
        }
        buffer.writeUtf(s);
        buffer.writeCollection(msg.jobs, (buf, v) -> UIJob.toNetwork((net.minecraft.network.RegistryFriendlyByteBuf) buf, v));
        buffer.writeBlockPos(msg.flagPos);
    }

    public static ShowItemJobsMessage decode(RegistryFriendlyByteBuf buffer) {
        String block = buffer.readUtf();
        Ingredient requestedItem = Ingredient.EMPTY;
        if (!block.isEmpty()) {
            requestedItem = Ingredients.fromString(block);
        }
        return new ShowItemJobsMessage(
                requestedItem,
                ImmutableList.copyOf(buffer.readList(b -> UIJob.fromNetwork((net.minecraft.network.RegistryFriendlyByteBuf) b))),
                buffer.readBlockPos()
        );
    }

    public void handle(
            Channel.Context ctx
    ) {
        ToClientMessage.handle(ctx, this);
    }

    @Override
    public void runOnClient() {
        ClientAccess.openItemJobs(requestedItem, jobs, flagPos);
    }
}
