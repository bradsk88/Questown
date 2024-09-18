package ca.bradj.questown.core.network;

import ca.bradj.questown.QT;
import ca.bradj.questown.gui.ClientAccess;
import ca.bradj.questown.mc.Compat;
import com.google.common.collect.ImmutableList;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

public record OnScreenTextMessage(
        String key, Object... args
) {

    public static void encode(OnScreenTextMessage msg, FriendlyByteBuf buffer) {
        buffer.writeUtf(msg.key());
        buffer.writeInt(msg.args.length);
        for (Object arg : msg.args) {
            buffer.writeUtf(arg.toString());
        }
    }

    public static OnScreenTextMessage decode(FriendlyByteBuf buffer) {
        String key = buffer.readUtf();
        int numArgs = buffer.readInt();
        ImmutableList.Builder<String> b = ImmutableList.builder();
        for (int i = 0; i < numArgs; i++) {
            b.add(buffer.readUtf());
        }
        return new OnScreenTextMessage(
                key, b.build().toArray()
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
                        ClientAccess.showHint(Compat.translatable(
                                key,
                                args
                        ));
                        success.set(true);
                    }
            );
        }).exceptionally(OnScreenTextMessage::logError);
        ctx.get().setPacketHandled(true);

    }

    private static Void logError(Throwable ex) {
        QT.GUI_LOGGER.error("Failed to send wand message to player", ex);
        return null;
    }
}
