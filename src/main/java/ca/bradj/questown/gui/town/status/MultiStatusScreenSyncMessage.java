package ca.bradj.questown.gui.town.status;

import ca.bradj.questown.QT;
import ca.bradj.questown.core.UtilClean;
import ca.bradj.questown.gui.StatusPacket;
import ca.bradj.questown.mc.Compat;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import ca.bradj.questown.loader.Channel;
import ca.bradj.questown.loader.Loader;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.List;
import java.util.UUID;

public record MultiStatusScreenSyncMessage(
        MultiStatusScreen.SyncedData data
) {

    public static void encode(
            MultiStatusScreenSyncMessage msg,
            RegistryFriendlyByteBuf buffer
    ) {
        buffer.writeMap(
                msg.data.villagerStatuses(), (b, k) -> b.writeUUID(k), StatusPacket::toNetwork
        );
        buffer.writeMap(
                msg.data.items(), (b, k) -> b.writeUUID(k), (b, v) ->
                        b.writeCollection(v, (bb, item) -> bb.writeResourceLocation(Compat.getItemId(item)))
        );
        buffer.writeBoolean(msg.data.villagerArrivingInMorning());
    }

    public static MultiStatusScreenSyncMessage decode(RegistryFriendlyByteBuf buffer) {
        HashMap<UUID, StatusPacket> data = buffer.readMap(
                HashMap::new,
                b -> b.readUUID(),
                StatusPacket::fromNetwork
        );
        HashMap<UUID, ImmutableList<Item>> data2 = buffer.readMap(
                HashMap::new,
                b -> b.readUUID(),
                MultiStatusScreenSyncMessage::readItemsFromBuffer
        );
        boolean arrivingInMorning = buffer.readBoolean();
        return new MultiStatusScreenSyncMessage(new MultiStatusScreen.SyncedData(
                data, data2, arrivingInMorning
        ));
    }

    private static @NotNull ImmutableList<Item> readItemsFromBuffer(net.minecraft.network.FriendlyByteBuf b) {
        List<Item> dat = b.readList(bb -> {
            ResourceLocation resourceLocation = b.readResourceLocation();
            try {
                return Loader.itemFromId(resourceLocation);
            } catch (Exception e) {
                QT.GUI_LOGGER.error("Failed to get item {}", resourceLocation, e);
            }
            return null;
        });
        return ImmutableList.copyOf(dat);
    }

    public void handle(
            Channel.Context ctx
    ) {
        ctx.enqueueWork(() -> Loader.runClient(() -> MultiStatusScreen.syncedData = new MultiStatusScreen.SyncedData(
                ImmutableMap.copyOf(data.villagerStatuses()),
                UtilClean.deepCopy(data.items()),
                data.villagerArrivingInMorning()
        ))).exceptionally(MultiStatusScreenSyncMessage::logError);

    }

    private static Void logError(Throwable ex) {
        QT.GUI_LOGGER.error("Failed to send villagers menu data to player", ex);
        return null;
    }
}
