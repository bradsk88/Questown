package ca.bradj.questown.core.network;

import ca.bradj.questown.QT;
import ca.bradj.questown.core.init.TilesInit;
import ca.bradj.questown.jobs.requests.WorkRequest;
import ca.bradj.questown.town.TownFlagBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.Optional;
import java.util.function.Supplier;

public record RemoveWorkFromUIMessage(
        WorkRequest requested,
        int flagX, int flagY, int flagZ
) {

    public static void encode(RemoveWorkFromUIMessage msg, FriendlyByteBuf buffer) {
        msg.requested.toNetwork(buffer);
        buffer.writeInt(msg.flagX);
        buffer.writeInt(msg.flagY);
        buffer.writeInt(msg.flagZ);

    }

    public static RemoveWorkFromUIMessage decode(FriendlyByteBuf buffer) {
        WorkRequest requested = WorkRequest.fromNetwork(buffer);
        int flagX = buffer.readInt();
        int flagY = buffer.readInt();
        int flagZ = buffer.readInt();
        return new RemoveWorkFromUIMessage(requested, flagX, flagY, flagZ);
    }


    public void handle(
            Supplier<NetworkEvent.Context> ctx
    ) {
        ctx.get().enqueueWork(() -> {
            // Work that needs to be thread-safe (most work)
            ServerPlayer sender = ctx.get().getSender(); // the client that sent this packet
            // Do stuff
            Optional<TownFlagBlockEntity> flag = sender.getLevel()
                    .getBlockEntity(new BlockPos(flagX, flagY, flagZ), TilesInit.TOWN_FLAG.get());
            if (flag.isEmpty()) {
                QT.GUI_LOGGER.error("No flag at position {}, {}, {}. Work will not be added.", flagX, flagY, flagZ);
                return;
            }
            flag.get().getWorkHandle().removeWorkRequest(requested);
            flag.get().openJobsMenu(sender);
        });
        ctx.get().setPacketHandled(true);

    }
}
