package ca.bradj.questown.core.network;

import ca.bradj.questown.QT;
import ca.bradj.questown.core.init.TilesInit;
import ca.bradj.questown.gui.Ingredients;
import ca.bradj.questown.town.TownFlagBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraftforge.network.NetworkEvent;

import java.util.Optional;
import java.util.function.Supplier;

public record OpenItemJobsMessage(
        BlockPos flagPos,
        Ingredient itemToShowJobsFor
) {

    public static void encode(
            OpenItemJobsMessage msg,
            FriendlyByteBuf buffer
    ) {
        buffer.writeBlockPos(msg.flagPos);
        buffer.writeUtf(Ingredients.toString(msg.itemToShowJobsFor()));
    }

    public static OpenItemJobsMessage decode(FriendlyByteBuf buffer) {
        BlockPos flagPos = buffer.readBlockPos();
        Ingredient ing = Ingredients.fromString(buffer.readUtf());
        return new OpenItemJobsMessage(flagPos, ing);
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
            flag.get().getVillagerHandle().showItemJobsUI(sender, itemToShowJobsFor);
        });
        ctx.get().setPacketHandled(true);

    }
}
