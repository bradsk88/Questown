package ca.bradj.questown.core.network;

import ca.bradj.questown.QT;
import ca.bradj.questown.core.init.TilesInit;
import ca.bradj.questown.loader.Channel;
import ca.bradj.questown.town.entity.TownFlagBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;

import java.util.Optional;

/**
 * Toggles the board's "auto-request" setting (F2): when on, acquiring a root job
 * auto-posts its initial requests. Client sends the NEW state; the server applies it
 * and re-opens the board menu so the checkbox resyncs.
 */
public record ToggleBoardAutoRequestMessage(
        boolean newState,
        int flagX, int flagY, int flagZ
) {

    public static void encode(
            ToggleBoardAutoRequestMessage msg,
            RegistryFriendlyByteBuf buffer
    ) {
        buffer.writeBoolean(msg.newState);
        buffer.writeInt(msg.flagX);
        buffer.writeInt(msg.flagY);
        buffer.writeInt(msg.flagZ);
    }

    public static ToggleBoardAutoRequestMessage decode(RegistryFriendlyByteBuf buffer) {
        boolean newState = buffer.readBoolean();
        int flagX = buffer.readInt();
        int flagY = buffer.readInt();
        int flagZ = buffer.readInt();
        return new ToggleBoardAutoRequestMessage(newState, flagX, flagY, flagZ);
    }

    public void handle(
            Channel.Context ctx
    ) {
        ctx.enqueueWork(() -> {
            ServerPlayer sender = (ServerPlayer) ctx.sender();
            Optional<TownFlagBlockEntity> flag = sender.level()
                                                       .getBlockEntity(
                                                               new BlockPos(flagX, flagY, flagZ),
                                                               TilesInit.TOWN_FLAG.get()
                                                       );
            if (flag.isEmpty()) {
                QT.GUI_LOGGER.error("No flag at position {}, {}, {}. Auto-request toggle ignored.", flagX, flagY, flagZ);
                return;
            }
            flag.get().setBoardAutoRequest(newState);
            flag.get().setChanged();
            QT.FLAG_LOGGER.info("Board auto-request set to {} by {}", newState, sender.getName().getString());
            // Re-open the board so the client's checkbox resyncs from the server state.
            flag.get().openJobsMenu(sender, false);
        });
    }
}
