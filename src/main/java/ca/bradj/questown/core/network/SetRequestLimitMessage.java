package ca.bradj.questown.core.network;

import ca.bradj.questown.QT;
import ca.bradj.questown.core.init.TilesInit;
import ca.bradj.questown.jobs.requests.WorkRequest;
import ca.bradj.questown.town.entity.TownFlagBlockEntity;
import net.minecraft.core.BlockPos;
import ca.bradj.questown.loader.Channel;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import java.util.Optional;

/**
 * Moves a board request's pause limit by a step from the job board's - / +. A step, not a value,
 * so rapid clicks all count.
 */
public record SetRequestLimitMessage(
        WorkRequest requested,
        int delta,
        int flagX, int flagY, int flagZ
) {

    public static void encode(
            SetRequestLimitMessage msg,
            FriendlyByteBuf buffer
    ) {
        msg.requested.toNetwork(buffer);
        buffer.writeInt(msg.delta);
        buffer.writeInt(msg.flagX);
        buffer.writeInt(msg.flagY);
        buffer.writeInt(msg.flagZ);
    }

    public static SetRequestLimitMessage decode(FriendlyByteBuf buffer) {
        WorkRequest requested = WorkRequest.fromNetwork(buffer);
        int delta = buffer.readInt();
        return new SetRequestLimitMessage(requested, delta, buffer.readInt(), buffer.readInt(), buffer.readInt());
    }

    public void handle(
            Channel.Context ctx
    ) {
        ctx.enqueueWork(() -> {
            ServerPlayer sender = (ServerPlayer) ctx.sender();
            Optional<TownFlagBlockEntity> flag = sender.getLevel().getBlockEntity(
                    new BlockPos(flagX, flagY, flagZ), TilesInit.TOWN_FLAG.get()
            );
            if (flag.isEmpty()) {
                QT.GUI_LOGGER.error("No flag at position {}, {}, {}. Limit not set.", flagX, flagY, flagZ);
                return;
            }
            flag.get().getWorkHandle().adjustRequestLimit(requested, delta);
            flag.get().openJobsMenu(sender, false);
        });
    }
}
