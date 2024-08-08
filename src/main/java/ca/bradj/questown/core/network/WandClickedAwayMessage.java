package ca.bradj.questown.core.network;

import ca.bradj.questown.QT;
import ca.bradj.questown.gui.ClientAccess;
import ca.bradj.questown.jobs.JobID;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

public record WandClickedAwayMessage(
        BlockPos flagPos
) {

    public static void encode(WandClickedAwayMessage msg, FriendlyByteBuf buffer) {
         buffer.writeInt(msg.flagPos.getX());
         buffer.writeInt(msg.flagPos.getY());
         buffer.writeInt(msg.flagPos.getZ());
    }

    public static WandClickedAwayMessage decode(FriendlyByteBuf buffer) {
        return new WandClickedAwayMessage(
                new BlockPos(buffer.readInt(), buffer.readInt(), buffer.readInt())
        );
    }

    public void handle(
            Supplier<NetworkEvent.Context> ctx
    ) {
        final AtomicBoolean success = new AtomicBoolean(false);
        ctx.get().enqueueWork(() -> {
            DistExecutor.unsafeRunWhenOn(
                    Dist.CLIENT,
                    () -> () -> {
                        ClientAccess.showWandHint(flagPos);
                        success.set(true);
                    }
            );
        }).exceptionally(WandClickedAwayMessage::logError);
        ctx.get().setPacketHandled(true);

    }

    private static Void logError(Throwable ex) {
        QT.GUI_LOGGER.error("Failed to send wand message to player", ex);
        return null;
    }
}
