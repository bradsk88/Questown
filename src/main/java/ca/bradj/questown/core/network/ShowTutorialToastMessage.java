package ca.bradj.questown.core.network;

import ca.bradj.questown.QT;
import ca.bradj.questown.gui.ClientAccess;
import ca.bradj.questown.mc.Compat;
import ca.bradj.questown.loader.Channel;
import ca.bradj.questown.loader.Loader;
import net.minecraft.network.RegistryFriendlyByteBuf;

import java.util.concurrent.atomic.AtomicBoolean;

public record ShowTutorialToastMessage(
        String titleKey, String descriptionKey
) {

    public static void encode(ShowTutorialToastMessage msg, RegistryFriendlyByteBuf buffer) {
        buffer.writeUtf(msg.titleKey());
        buffer.writeUtf(msg.descriptionKey());
    }

    public static ShowTutorialToastMessage decode(RegistryFriendlyByteBuf buffer) {
        String titleKey = buffer.readUtf();
        String descriptionKey = buffer.readUtf();
        return new ShowTutorialToastMessage(titleKey, descriptionKey);
    }

    public void handle(
            Channel.Context ctx
    ) {
        final AtomicBoolean success = new AtomicBoolean(false);
        ctx.enqueueWork(() -> Loader.runClient(() -> {
            ClientAccess.showTutorialToast(
                    Compat.translatable(titleKey),
                    Compat.translatable(descriptionKey)
            );
            success.set(true);
        })).exceptionally(ShowTutorialToastMessage::logError);
    }

    private static Void logError(Throwable ex) {
        QT.GUI_LOGGER.error("Failed to send tutorial toast to player", ex);
        return null;
    }
}
