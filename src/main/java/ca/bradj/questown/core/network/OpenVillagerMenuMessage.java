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

public record OpenVillagerMenuMessage(
        int flagX, int flagY, int flagZ, UUID villagerUUID, String type, boolean showInventoryTab
) {

    public static final String INVENTORY = "inventory";
    public static final String QUESTS = "quests";
    public static final String STATS = "stats";
    public static final String SKILLS = "skills";
    public static final String CHANGE_ROOT = "change_root";
    public static final String ECONOMICS = "economics";
    public static final String BOP = "bop";

    public static void encode(OpenVillagerMenuMessage msg, RegistryFriendlyByteBuf buffer) {
        buffer.writeInt(msg.flagX());
        buffer.writeInt(msg.flagY());
        buffer.writeInt(msg.flagZ());
        buffer.writeUUID(msg.villagerUUID());
        buffer.writeUtf(msg.type());
        buffer.writeBoolean(msg.showInventoryTab());
    }

    public static OpenVillagerMenuMessage decode(RegistryFriendlyByteBuf buffer) {
        int flagX = buffer.readInt();
        int flagY = buffer.readInt();
        int flagZ = buffer.readInt();
        UUID id = buffer.readUUID();
        String type = buffer.readUtf();
        boolean showInventoryTab = buffer.readBoolean();
        return new OpenVillagerMenuMessage(flagX, flagY, flagZ, id, type, showInventoryTab);
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
            flag.getVillagerHandle().showUI(sender, type(), villagerUUID(), showInventoryTab());
        }).exceptionally((ex) -> {
            QT.GUI_LOGGER.error("Failed to open villager menu", ex);
            return null;
        });

    }
}
