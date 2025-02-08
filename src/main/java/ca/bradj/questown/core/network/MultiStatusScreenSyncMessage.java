package ca.bradj.questown.core.network;

import ca.bradj.questown.QT;
import ca.bradj.questown.core.Pair;
import ca.bradj.questown.core.UtilClean;
import ca.bradj.questown.gui.MultiStatusScreen;
import ca.bradj.questown.gui.SessionUniqueOrdinals;
import ca.bradj.questown.jobs.IStatus;
import ca.bradj.questown.jobs.JobID;
import ca.bradj.questown.jobs.Jobs;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

public record MultiStatusScreenSyncMessage(
        MultiStatusScreen.SyncedData data
) {

    public static void encode(
            MultiStatusScreenSyncMessage msg,
            FriendlyByteBuf buffer
    ) {
        buffer.writeMap(
                msg.data.villagers(), FriendlyByteBuf::writeUUID, (b, v) -> {
                    Jobs.writeIdToNetwork(b, v.a());
                    b.writeInt(SessionUniqueOrdinals.getOrdinal(v.b()));
                }
        );
        buffer.writeMap(
                msg.data.items(), FriendlyByteBuf::writeUUID, (b, v) ->
                        b.writeCollection(v, (bb, item) -> bb.writeResourceLocation(item.getRegistryName()))
        );
    }

    public static MultiStatusScreenSyncMessage decode(FriendlyByteBuf buffer) {
        HashMap<UUID, Pair<JobID, IStatus<?>>> data = buffer.readMap(
                HashMap::new,
                FriendlyByteBuf::readUUID,
                b -> new Pair<>(
                        Jobs.getIdFromNetwork(b),
                        SessionUniqueOrdinals.getStatus(b.readInt())
                )
        );
        HashMap<UUID, ImmutableList<Item>> data2 = buffer.readMap(
                HashMap::new,
                FriendlyByteBuf::readUUID,
                MultiStatusScreenSyncMessage::readItemsFromBuffer
        );
        return new MultiStatusScreenSyncMessage(new MultiStatusScreen.SyncedData(
                data, data2
        ));
    }

    private static @NotNull ImmutableList<Item> readItemsFromBuffer(FriendlyByteBuf b) {
        List<Item> dat = b.readList(bb -> {
            ResourceLocation resourceLocation = b.readResourceLocation();
            try {
                return ForgeRegistries.ITEMS.getValue(resourceLocation);
            } catch (Exception e) {
                QT.GUI_LOGGER.error("Failed to get item {}", resourceLocation, e);
            }
            return null;
        });
        return ImmutableList.copyOf(dat);
    }

    public void handle(
            Supplier<NetworkEvent.Context> ctx
    ) {
        ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(
                Dist.CLIENT,
                () -> () -> MultiStatusScreen.syncedData = new MultiStatusScreen.SyncedData(
                        ImmutableMap.copyOf(data.villagers()),
                        UtilClean.deepCopy(data.items())
                )
        )).exceptionally(MultiStatusScreenSyncMessage::logError);
        ctx.get().setPacketHandled(true);

    }

    private static Void logError(Throwable ex) {
        QT.GUI_LOGGER.error("Failed to send villagers menu data to player", ex);
        return null;
    }
}
