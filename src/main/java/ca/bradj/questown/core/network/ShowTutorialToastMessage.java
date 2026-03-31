package ca.bradj.questown.core.network;

import ca.bradj.questown.QT;
import ca.bradj.questown.gui.ClientAccess;
import ca.bradj.questown.mc.Compat;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

public record ShowTutorialToastMessage(
        String titleKey, String descriptionKey
) {

    public static void encode(ShowTutorialToastMessage msg, FriendlyByteBuf buffer) {
        buffer.writeUtf(msg.titleKey());
        buffer.writeUtf(msg.descriptionKey());
    }

    public static ShowTutorialToastMessage decode(FriendlyByteBuf buffer) {
        String titleKey = buffer.readUtf();
        String descriptionKey = buffer.readUtf();
        return new ShowTutorialToastMessage(titleKey, descriptionKey);
    }

    public void handle(
            Supplier<NetworkEvent.Context> ctx
    ) {
        final AtomicBoolean success = new AtomicBoolean(false);
        ctx.get().enqueueWork(() -> {
            DistExecutor.unsafeRunWhenOn(
                    Dist.CLIENT,
                    () -> () -> {
                        ClientAccess.showTutorialToast(
                                Compat.translatable(titleKey),
                                Compat.translatable(descriptionKey)
                        );
                        success.set(true);
                    }
            );
        }).exceptionally(ShowTutorialToastMessage::logError);
        ctx.get().setPacketHandled(true);
    }

    private static Void logError(Throwable ex) {
        QT.GUI_LOGGER.error("Failed to send tutorial toast to player", ex);
        return null;
    }
}
