package ca.bradj.questown.core.network;

import ca.bradj.questown.QT;
import ca.bradj.questown.core.init.TilesInit;
import ca.bradj.questown.gui.FlagTabsEmbedding;
import ca.bradj.questown.town.entity.TownFlagBlockEntity;
import net.minecraft.core.BlockPos;
import ca.bradj.questown.loader.Channel;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;

import java.util.Optional;

public record OpenFlagMenuMessage(FlagTabsEmbedding.FlagInfo flag, String type) {

    public static final String QUESTS = "quests";
    public static final String VILLAGERS = "villagers";
    public static final String ECONOMICS = "economics";
    public static final String BOP = "blocks_of_progress";
    public static final String CRAFTING = "crafting";

    public static void encode(
            OpenFlagMenuMessage msg,
            RegistryFriendlyByteBuf buffer
    ) {
        buffer.writeBlockPos(msg.flag.flagPos());
        buffer.writeUtf(msg.type());
        buffer.writeBoolean(msg.flag.showBlockOfProgressTab());
    }

    public static OpenFlagMenuMessage decode(RegistryFriendlyByteBuf buffer) {
        BlockPos flag = buffer.readBlockPos();
        String type = buffer.readUtf();
        boolean show = buffer.readBoolean();
        return new OpenFlagMenuMessage(FlagTabsEmbedding.FlagInfo.dumb(flag, show, false), type);
    }


    public void handle(
            Channel.Context ctx
    ) {
        ctx.enqueueWork(() -> {
            // Work that needs to be thread-safe (most work)
            ServerPlayer sender = (ServerPlayer) ctx.sender(); // the client that sent this packet
            // Do stuff
            BlockPos p = flag().flagPos();
            if (!(sender.level().getBlockEntity(p) instanceof TownFlagBlockEntity entity)) {
                QT.GUI_LOGGER.error(
                        "No flag at position {}, {}, {}. Quest will not be removed.",
                        p.getX(),
                        p.getY(),
                        p.getZ()
                );
                return;
            }
            entity.menus.showUI(sender, type(), flag(), entity.getBlocksOfProgress());
        }).exceptionally((ex) -> {
            QT.GUI_LOGGER.error("Failed to open flag menu", ex);
            return null;
        });

    }
}
