package ca.bradj.questown.core.network;

import ca.bradj.questown.gui.ClientAccess;
import ca.bradj.questown.gui.Ingredients;
import ca.bradj.questown.gui.UIJob;
import com.google.common.collect.ImmutableList;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public record ShowItemJobsMessage(
        Ingredient requestedItem,
        ImmutableList<UIJob> jobs
) implements ClientRunnable {

    public static void encode(
            ShowItemJobsMessage msg,
            FriendlyByteBuf buffer
    ) {
        buffer.writeUtf(Ingredients.toString(msg.requestedItem));
        buffer.writeCollection(msg.jobs, UIJob::toNetwork);
    }

    public static ShowItemJobsMessage decode(FriendlyByteBuf buffer) {
        return new ShowItemJobsMessage(
                Ingredients.fromString(buffer.readUtf()),
                ImmutableList.copyOf(buffer.readList(UIJob::fromNetwork))
        );
    }

    public void handle(
            Supplier<NetworkEvent.Context> ctx
    ) {
        ToClientMessage.handle(ctx, this);
    }

    @Override
    public void runOnClient() {
        ClientAccess.openItemJobs(requestedItem, jobs);
    }
}
