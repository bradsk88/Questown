package ca.bradj.questown.core.network;

import ca.bradj.questown.QT;
import ca.bradj.questown.core.init.TilesInit;
import ca.bradj.questown.jobs.JobID;
import ca.bradj.questown.jobs.Jobs;
import ca.bradj.questown.town.entity.TownFlagBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.Optional;
import java.util.function.Supplier;

public record OpenJobMessage(
        BlockPos flagPos,
        JobID jobToShow
) {

    public static void encode(
            OpenJobMessage msg,
            FriendlyByteBuf buffer
    ) {
        buffer.writeBlockPos(msg.flagPos);
        Jobs.writeIdToNetwork(buffer, msg.jobToShow);
    }

    public static OpenJobMessage decode(FriendlyByteBuf buffer) {
        BlockPos flagPos = buffer.readBlockPos();
        JobID j = Jobs.getIdFromNetwork(buffer);
        return new OpenJobMessage(flagPos, j);
    }


    public void handle(
            Supplier<NetworkEvent.Context> ctx
    ) {
        ctx.get().enqueueWork(() -> {
            // Work that needs to be thread-safe (most work)
            ServerPlayer sender = ctx.get().getSender(); // the client that sent this packet
            // Do stuff
            Optional<TownFlagBlockEntity> flag = sender.getLevel()
                                                       .getBlockEntity(
                                                               flagPos,
                                                               TilesInit.TOWN_FLAG.get()
                                                       );
            if (flag.isEmpty()) {
                QT.GUI_LOGGER.error("No flag at position {}. Quest will not be removed.", flagPos);
                return;
            }
            flag.get().getVillagerHandle().showJobUI(sender, jobToShow);
        });
        ctx.get().setPacketHandled(true);

    }
}
