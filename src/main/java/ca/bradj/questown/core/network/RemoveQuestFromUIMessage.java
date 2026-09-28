package ca.bradj.questown.core.network;

import ca.bradj.questown.QT;
import ca.bradj.questown.core.init.TilesInit;
import ca.bradj.questown.town.entity.TownFlagBlockEntity;
import net.minecraft.core.BlockPos;
import ca.bradj.questown.loader.Channel;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;

import java.util.Optional;
import java.util.UUID;

public record RemoveQuestFromUIMessage(
        UUID questIndex,
        int flagX, int flagY, int flagZ,
        boolean promptForConfirmation
) {

    public static void encode(RemoveQuestFromUIMessage msg, RegistryFriendlyByteBuf buffer) {
        buffer.writeUUID(msg.questIndex);
        buffer.writeInt(msg.flagX);
        buffer.writeInt(msg.flagY);
        buffer.writeInt(msg.flagZ);
        buffer.writeBoolean(msg.promptForConfirmation);
    }

    public static RemoveQuestFromUIMessage decode(RegistryFriendlyByteBuf buffer) {
        UUID questIndex = buffer.readUUID();
        int flagX = buffer.readInt();
        int flagY = buffer.readInt();
        int flagZ = buffer.readInt();
        boolean prompt = buffer.readBoolean();
        return new RemoveQuestFromUIMessage(questIndex, flagX, flagY, flagZ, prompt);
    }


    public void handle(
            Channel.Context ctx
    ) {
        ctx.enqueueWork(() -> {
            // Work that needs to be thread-safe (most work)
            ServerPlayer sender = (ServerPlayer) ctx.sender(); // the client that sent this packet
            // Do stuff
            if (!(sender.level().getBlockEntity(new BlockPos(flagX, flagY, flagZ)) instanceof TownFlagBlockEntity flag)) {
                QT.GUI_LOGGER.error("No flag at position {}, {}, {}. Quest will not be removed.", flagX, flagY, flagZ);
                return;
            }
            flag.getQuestHandle().requestRemovalOfQuestAtIndex(questIndex, sender, promptForConfirmation);
        });

    }
}
