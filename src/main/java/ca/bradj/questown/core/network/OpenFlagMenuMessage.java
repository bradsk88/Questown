package ca.bradj.questown.core.network;

import ca.bradj.questown.QT;
import ca.bradj.questown.core.init.TilesInit;
import ca.bradj.questown.town.TownFlagBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

public record OpenFlagMenuMessage(
        int flagX, int flagY, int flagZ, String type
) {

    public static final String QUESTS = "quests";
    public static final String VILLAGERS = "villagers";

    public static void encode(OpenFlagMenuMessage msg, FriendlyByteBuf buffer) {
        buffer.writeInt(msg.flagX());
        buffer.writeInt(msg.flagY());
        buffer.writeInt(msg.flagZ());
        buffer.writeUtf(msg.type());
    }

    public static OpenFlagMenuMessage decode(FriendlyByteBuf buffer) {
        int flagX = buffer.readInt();
        int flagY = buffer.readInt();
        int flagZ = buffer.readInt();
        String type = buffer.readUtf();
        return new OpenFlagMenuMessage(flagX, flagY, flagZ, type);
    }


    public void handle(
            Supplier<NetworkEvent.Context> ctx
    ) {
        ctx.get().enqueueWork(() -> {
            // Work that needs to be thread-safe (most work)
            ServerPlayer sender = ctx.get().getSender(); // the client that sent this packet
            // Do stuff
            BlockPos flagPos = new BlockPos(flagX, flagY, flagZ);
            Optional<TownFlagBlockEntity> flag = sender.getLevel()
                                                       .getBlockEntity(flagPos, TilesInit.TOWN_FLAG.get());
            if (flag.isEmpty()) {
                QT.GUI_LOGGER.error("No flag at position {}, {}, {}. Quest will not be removed.", flagX, flagY, flagZ);
                return;
            }
            flag.get().menus.showUI(sender, type(), flagPos);
        }).exceptionally((ex) -> {
            QT.GUI_LOGGER.error("Failed to open flag menu", ex);
            return null;
        });
        ctx.get().setPacketHandled(true);

    }
}
