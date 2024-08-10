package ca.bradj.questown.core.network;

import ca.bradj.questown.QT;
import ca.bradj.questown.core.advancements.VisitorTrigger;
import ca.bradj.questown.core.init.AdvancementsInit;
import ca.bradj.questown.town.TownFlagBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public record AddWorkFromUIMessage(
        ItemStack requested,
        int flagX, int flagY, int flagZ
) {

    public static void encode(AddWorkFromUIMessage msg, FriendlyByteBuf buffer) {
        buffer.writeItem(msg.requested);
        buffer.writeInt(msg.flagX);
        buffer.writeInt(msg.flagY);
        buffer.writeInt(msg.flagZ);

    }

    public static AddWorkFromUIMessage decode(FriendlyByteBuf buffer) {
        ItemStack requested = buffer.readItem();
        int flagX = buffer.readInt();
        int flagY = buffer.readInt();
        int flagZ = buffer.readInt();
        return new AddWorkFromUIMessage(requested, flagX, flagY, flagZ);
    }


    public void handle(
            Supplier<NetworkEvent.Context> ctx
    ) {
        ctx.get().enqueueWork(() -> {
            // Work that needs to be thread-safe (most work)
            ServerPlayer sender = ctx.get().getSender(); // the client that sent this packet
            // Do stuff
            BlockEntity flag = sender.getLevel()
                    .getBlockEntity(new BlockPos(flagX, flagY, flagZ));
            if (!(flag instanceof TownFlagBlockEntity tfbe)) {
                QT.GUI_LOGGER.error("No flag at position {}, {}, {}. Work will not be added.", flagX, flagY, flagZ);
                return;
            }
            AdvancementsInit.VISITOR_TRIGGER.trigger(ctx.get().getSender(), VisitorTrigger.Triggers.FirstJobRequest);
            tfbe.getWorkHandle().requestWork(requested.getItem());
            tfbe.openJobsMenu(sender);
        });
        ctx.get().setPacketHandled(true);

    }
}
