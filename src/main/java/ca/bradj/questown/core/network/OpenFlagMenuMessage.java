package ca.bradj.questown.core.network;

import ca.bradj.questown.QT;
import ca.bradj.questown.core.init.TilesInit;
import ca.bradj.questown.gui.FlagTabsEmbedding;
import ca.bradj.questown.town.TownFlagBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.Optional;
import java.util.function.Supplier;

public record OpenFlagMenuMessage(FlagTabsEmbedding.FlagInfo flag, String type) {

    public static final String QUESTS = "quests";
    public static final String VILLAGERS = "villagers";
    public static final String ECONOMICS = "economics";
    public static final String BOP = "blocks_of_progress";

    public static void encode(
            OpenFlagMenuMessage msg,
            FriendlyByteBuf buffer
    ) {
        buffer.writeBlockPos(msg.flag.flagPos());
        buffer.writeUtf(msg.type());
        buffer.writeBoolean(msg.flag.showBlockOfProgressTab());
    }

    public static OpenFlagMenuMessage decode(FriendlyByteBuf buffer) {
        BlockPos flag = buffer.readBlockPos();
        String type = buffer.readUtf();
        boolean show = buffer.readBoolean();
        return new OpenFlagMenuMessage(new FlagTabsEmbedding.FlagInfo(flag, show), type);
    }


    public void handle(
            Supplier<NetworkEvent.Context> ctx
    ) {
        ctx.get().enqueueWork(() -> {
            // Work that needs to be thread-safe (most work)
            ServerPlayer sender = ctx.get().getSender(); // the client that sent this packet
            // Do stuff
            BlockPos p = flag().flagPos();
            Optional<TownFlagBlockEntity> entity = sender.getLevel().getBlockEntity(p, TilesInit.TOWN_FLAG.get());
            if (entity.isEmpty()) {
                QT.GUI_LOGGER.error(
                        "No flag at position {}, {}, {}. Quest will not be removed.",
                        p.getX(),
                        p.getY(),
                        p.getZ()
                );
                return;
            }
            entity.get().menus.showUI(sender, type(), flag(), entity.get().getBlocksOfProgress());
        }).exceptionally((ex) -> {
            QT.GUI_LOGGER.error("Failed to open flag menu", ex);
            return null;
        });
        ctx.get().setPacketHandled(true);

    }
}
