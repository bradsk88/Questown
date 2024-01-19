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

public record OpenVillagerMenuMessage(
        int flagX, int flagY, int flagZ, UUID villagerUUID, String type
) {

    public static final String INVENTORY = "inventory";
    public static final String QUESTS = "quests";
    public static final String STATS = "stats";

    public static void encode(OpenVillagerMenuMessage msg, FriendlyByteBuf buffer) {
        buffer.writeInt(msg.flagX());
        buffer.writeInt(msg.flagY());
        buffer.writeInt(msg.flagZ());
        buffer.writeUUID(msg.villagerUUID());
        buffer.writeUtf(msg.type());
    }

    public static OpenVillagerMenuMessage decode(FriendlyByteBuf buffer) {
        int flagX = buffer.readInt();
        int flagY = buffer.readInt();
        int flagZ = buffer.readInt();
        UUID id = buffer.readUUID();
        String type = buffer.readUtf();
        return new OpenVillagerMenuMessage(flagX, flagY, flagZ, id, type);
    }


    public void handle(
            Supplier<NetworkEvent.Context> ctx
    ) {
        ctx.get().enqueueWork(() -> {
            // Work that needs to be thread-safe (most work)
            ServerPlayer sender = ctx.get().getSender(); // the client that sent this packet
            // Do stuff
            Optional<TownFlagBlockEntity> flag = sender.getLevel()
                    .getBlockEntity(new BlockPos(flagX, flagY, flagZ), TilesInit.TOWN_FLAG.get());
            if (flag.isEmpty()) {
                QT.GUI_LOGGER.error("No flag at position {}, {}, {}. Quest will not be removed.", flagX, flagY, flagZ);
                return;
            }
            flag.get().getVillagerHandle().showUI(sender, type(), villagerUUID());
        });
        ctx.get().setPacketHandled(true);

    }
}
