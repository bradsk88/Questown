package ca.bradj.questown.core.network;

import ca.bradj.questown.QT;
import ca.bradj.questown.core.init.TilesInit;
import ca.bradj.questown.jobs.requests.WorkRequest;
import ca.bradj.questown.town.entity.TownFlagBlockEntity;
import net.minecraft.core.BlockPos;
import ca.bradj.questown.loader.Channel;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;

import java.util.Optional;

public record RemoveWorkFromUIMessage(
        WorkRequest requested,
        int flagX, int flagY, int flagZ
) {

    public static void encode(
            RemoveWorkFromUIMessage msg,
            RegistryFriendlyByteBuf buffer
    ) {
        msg.requested.toNetwork(buffer);
        buffer.writeInt(msg.flagX);
        buffer.writeInt(msg.flagY);
        buffer.writeInt(msg.flagZ);

    }

    public static RemoveWorkFromUIMessage decode(RegistryFriendlyByteBuf buffer) {
        WorkRequest requested = WorkRequest.fromNetwork(buffer);
        int flagX = buffer.readInt();
        int flagY = buffer.readInt();
        int flagZ = buffer.readInt();
        return new RemoveWorkFromUIMessage(requested, flagX, flagY, flagZ);
    }


    public void handle(
            Channel.Context ctx
    ) {
        ctx.enqueueWork(() -> {
            // Work that needs to be thread-safe (most work)
            ServerPlayer sender = (ServerPlayer) ctx.sender(); // the client that sent this packet
            // Do stuff
            Optional<TownFlagBlockEntity> flag = sender.level()
                                                       .getBlockEntity(
                                                               new BlockPos(flagX, flagY, flagZ),
                                                               TilesInit.TOWN_FLAG.get()
                                                       );
            if (flag.isEmpty()) {
                QT.GUI_LOGGER.error("No flag at position {}, {}, {}. Work will not be added.", flagX, flagY, flagZ);
                return;
            }
            flag.get().getWorkHandle().removeWorkRequest(requested);
            flag.get().openJobsMenu(sender, false);
        });

    }
}
