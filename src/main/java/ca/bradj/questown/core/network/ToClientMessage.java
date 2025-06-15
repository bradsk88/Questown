package ca.bradj.questown.core.network;

import ca.bradj.questown.QT;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

public final class ToClientMessage {
    public static void handle(
            Supplier<NetworkEvent.Context> ctx,
            ClientRunnable o
    ) {
        final AtomicBoolean success = new AtomicBoolean(false);
        ctx.get().enqueueWork(() -> {
            DistExecutor.unsafeRunWhenOn(
                    Dist.CLIENT,
                    () -> () -> {
                        o.runOnClient();
                        success.set(true);
                    }
            );
        }).exceptionally(err -> logError(o.getClass(), err));
        ctx.get().setPacketHandled(true);
    }

    private static Void logError(
            Class<? extends ClientRunnable> aClass,
            Throwable ex
    ) {
        String name = aClass.getName();
        QT.GUI_LOGGER.error("Failed to send {} data to player", name, ex);
        return null;
    }
}
