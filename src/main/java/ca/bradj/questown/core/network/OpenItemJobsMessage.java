package ca.bradj.questown.core.network;

import ca.bradj.questown.QT;
import ca.bradj.questown.core.init.TilesInit;
import ca.bradj.questown.gui.Ingredients;
import ca.bradj.questown.town.entity.TownFlagBlockEntity;
import net.minecraft.core.BlockPos;
import ca.bradj.questown.loader.Channel;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.crafting.Ingredient;

import java.util.Optional;

public record OpenItemJobsMessage(
        BlockPos flagPos,
        Ingredient itemToShowJobsFor
) {

    public static void encode(
            OpenItemJobsMessage msg,
            RegistryFriendlyByteBuf buffer
    ) {
        buffer.writeBlockPos(msg.flagPos);
        buffer.writeUtf(Ingredients.toString(msg.itemToShowJobsFor()));
    }

    public static OpenItemJobsMessage decode(RegistryFriendlyByteBuf buffer) {
        BlockPos flagPos = buffer.readBlockPos();
        Ingredient ing = Ingredients.fromString(buffer.readUtf());
        return new OpenItemJobsMessage(flagPos, ing);
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
                                                               flagPos,
                                                               TilesInit.TOWN_FLAG.get()
                                                       );
            if (flag.isEmpty()) {
                QT.GUI_LOGGER.error("No flag at position {}. Quest will not be removed.", flagPos);
                return;
            }
            flag.get().getVillagerHandle().showItemJobsUI(sender, itemToShowJobsFor);
        });

    }
}
