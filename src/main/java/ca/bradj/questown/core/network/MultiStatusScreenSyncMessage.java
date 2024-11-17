package ca.bradj.questown.core.network;

import ca.bradj.questown.QT;
import ca.bradj.questown.core.UtilClean;
import ca.bradj.questown.gui.MultiStatusScreen;
import ca.bradj.questown.gui.SessionUniqueOrdinals;
import ca.bradj.questown.jobs.IStatus;
import ca.bradj.questown.jobs.JobID;
import ca.bradj.questown.jobs.Jobs;
import com.google.common.collect.ImmutableMap;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.HashMap;
import java.util.UUID;
import java.util.function.Supplier;

public record MultiStatusScreenSyncMessage(
        MultiStatusScreen.SyncedData data
) {

    public static void encode(
            MultiStatusScreenSyncMessage msg,
            FriendlyByteBuf buffer
    ) {
        buffer.writeMap(msg.data.villagers(), FriendlyByteBuf::writeUUID, (b, v) -> {
            Jobs.writeIdToNetwork(b, v.a());
            b.writeInt(SessionUniqueOrdinals.getOrdinal(v.b()));
        });
    }

    public static MultiStatusScreenSyncMessage decode(FriendlyByteBuf buffer) {
        HashMap<UUID, UtilClean.Pair<JobID, IStatus<?>>> data = buffer.readMap(
                HashMap::new,
                FriendlyByteBuf::readUUID,
                b -> new UtilClean.Pair<>(
                        Jobs.getIdFromNetwork(b),
                        SessionUniqueOrdinals.getStatus(b.readInt())
                )
        );
        return new MultiStatusScreenSyncMessage(new MultiStatusScreen.SyncedData(data));
    }

    public void handle(
            Supplier<NetworkEvent.Context> ctx
    ) {
        ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(
                Dist.CLIENT,
                () -> () -> MultiStatusScreen.syncedData = new MultiStatusScreen.SyncedData(
                        ImmutableMap.copyOf(data.villagers())
                )
        )).exceptionally(MultiStatusScreenSyncMessage::logError);
        ctx.get().setPacketHandled(true);

    }

    private static Void logError(Throwable ex) {
        QT.GUI_LOGGER.error("Failed to send villagers menu data to player", ex);
        return null;
    }
}
