package ca.bradj.questown.core.network;

import ca.bradj.questown.QT;
import ca.bradj.questown.gui.ClientAccess;
import ca.bradj.questown.gui.villager.advancements.VillagerAdvancementsScreen;
import ca.bradj.questown.jobs.JobID;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

public record OpenVillagerAdvancementsMenuMessage(
        BlockPos flagPos, UUID villagerUUID, JobID currentJob
) {

    public static void encode(OpenVillagerAdvancementsMenuMessage msg, FriendlyByteBuf buffer) {
         buffer.writeInt(msg.flagPos.getX());
         buffer.writeInt(msg.flagPos.getY());
         buffer.writeInt(msg.flagPos.getZ());
         buffer.writeUUID(msg.villagerUUID);
         buffer.writeResourceLocation(msg.currentJob.id());
    }

    public static OpenVillagerAdvancementsMenuMessage decode(FriendlyByteBuf buffer) {
        return new OpenVillagerAdvancementsMenuMessage(
                new BlockPos(buffer.readInt(), buffer.readInt(), buffer.readInt()),
                buffer.readUUID(),
                JobID.fromRL(buffer.readResourceLocation())
        );
    }

    public void handle(
            Supplier<NetworkEvent.Context> ctx
    ) {
        final AtomicBoolean success = new AtomicBoolean(false);
        ctx.get().enqueueWork(() -> {
            DistExecutor.unsafeRunWhenOn(
                    Dist.CLIENT,
                    () -> () -> success.set(
                            ClientAccess.openVillagerAdvancements(flagPos, villagerUUID, currentJob)
                    )
            );
        }).exceptionally(OpenVillagerAdvancementsMenuMessage::logError);
        ctx.get().setPacketHandled(true);

    }

    private static Void logError(Throwable ex) {
        QT.GUI_LOGGER.error("Failed to open villager advancements screen", ex);
        return null;
    }
}
