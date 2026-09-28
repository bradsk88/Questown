package ca.bradj.questown.core.network;

import ca.bradj.questown.QT;
import ca.bradj.questown.gui.ClientAccess;
import ca.bradj.questown.loader.Channel;
import ca.bradj.questown.loader.Loader;
import net.minecraft.network.RegistryFriendlyByteBuf;

import java.util.concurrent.atomic.AtomicBoolean;

public record CloseScreensMessage() {

    public static void encode(CloseScreensMessage msg, RegistryFriendlyByteBuf buffer) {
    }

    public static CloseScreensMessage decode(RegistryFriendlyByteBuf buffer) {
        return new CloseScreensMessage();
    }

    public void handle(
            Channel.Context ctx
    ) {
        final AtomicBoolean success = new AtomicBoolean(false);
        ctx.enqueueWork(() -> Loader.runClient(() -> {
            ClientAccess.closeScreens();
            success.set(true);
        })).exceptionally(CloseScreensMessage::logError);

    }

    private static Void logError(Throwable ex) {
        QT.GUI_LOGGER.error("Failed to send wand message to player", ex);
        return null;
    }
}
