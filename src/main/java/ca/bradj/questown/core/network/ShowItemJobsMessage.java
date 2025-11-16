package ca.bradj.questown.core.network;

import ca.bradj.questown.gui.ClientAccess;
import ca.bradj.questown.gui.Ingredients;
import ca.bradj.questown.gui.UIJob;
import com.google.common.collect.ImmutableList;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public record ShowItemJobsMessage(
        Ingredient requestedItem,
        ImmutableList<UIJob> jobs,
        BlockPos flagPos
) implements ClientRunnable {

    public static void encode(
            ShowItemJobsMessage msg,
            FriendlyByteBuf buffer
    ) {
        String s = "";
        if (!msg.requestedItem.isEmpty()) {
            s = Ingredients.toString(msg.requestedItem);
        }
        buffer.writeUtf(s);
        buffer.writeCollection(msg.jobs, UIJob::toNetwork);
        buffer.writeBlockPos(msg.flagPos);
    }

    public static ShowItemJobsMessage decode(FriendlyByteBuf buffer) {
        String block = buffer.readUtf();
        Ingredient requestedItem = Ingredient.EMPTY;
        if (!block.isEmpty()) {
            requestedItem = Ingredients.fromString(block);
        }
        return new ShowItemJobsMessage(
                requestedItem,
                ImmutableList.copyOf(buffer.readList(UIJob::fromNetwork)),
                buffer.readBlockPos()
        );
    }

    public void handle(
            Supplier<NetworkEvent.Context> ctx
    ) {
        ToClientMessage.handle(ctx, this);
    }

    @Override
    public void runOnClient() {
        ClientAccess.openItemJobs(requestedItem, jobs, flagPos);
    }
}
