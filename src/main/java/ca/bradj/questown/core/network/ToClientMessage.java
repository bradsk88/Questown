package ca.bradj.questown.core.network;

import ca.bradj.questown.QT;
import ca.bradj.questown.loader.Channel;
import ca.bradj.questown.loader.Loader;

import java.util.concurrent.atomic.AtomicBoolean;

public final class ToClientMessage {
    public static void handle(
            Channel.Context ctx,
            ClientRunnable o
    ) {
        final AtomicBoolean success = new AtomicBoolean(false);
        ctx.enqueueWork(() -> Loader.runClient(() -> {
            o.runOnClient();
            success.set(true);
        })).exceptionally(err -> logError(o.getClass(), err));
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
