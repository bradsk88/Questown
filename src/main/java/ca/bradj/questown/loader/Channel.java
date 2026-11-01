package ca.bradj.questown.loader;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.CompletableFuture;

/**
 * Loader-neutral message channel. Main code registers each message with its encoder/decoder over
 * {@link FriendlyByteBuf} and a handler, then sends via
 * {@link #sendToPlayer}/{@link #sendToAll}/{@link #sendToServer}; the active loader adapts it to
 * its own networking.
 *
 * <p>A {@link #message(Class, Direction)} registration is finalised by {@code handler(...)}; the
 * encoder and decoder must both be present (the message's {@code encode}/{@code decode} statics).
 */
public interface Channel {

    enum Direction { TO_CLIENT, TO_SERVER }

    <T> Builder<T> message(Class<T> type, Direction dir);

    /** Server to one player. */
    <T> void sendToPlayer(Player p, T msg);

    /** Server to every player in the level's dimension. */
    <T> void sendToAll(Level level, T msg);

    /** Client to the server it is connected to. */
    <T> void sendToServer(T msg);

    interface Builder<T> {
        Builder<T> encoder(Encoder<T> encoder);
        Builder<T> decoder(Decoder<T> decoder);
        /** Last call: finalises the registration. */
        Builder<T> handler(Handler<T> handler);
    }

    @FunctionalInterface
    interface Encoder<T> {
        void encode(T msg, FriendlyByteBuf buf);
    }

    @FunctionalInterface
    interface Decoder<T> {
        T decode(FriendlyByteBuf buf);
    }

    interface Handler<T> {
        void handle(T msg, Context ctx);
    }

    /**
     * The receiving-side context. Handlers run on the receiver (client for TO_CLIENT, server for
     * TO_SERVER); {@link #enqueueWork} schedules the body on that side's main thread, and
     * {@link #sender()} is the player the packet was sent to (TO_SERVER) or from (TO_CLIENT).
     * {@code sender()} is non-null for both directions but typed as {@code Player}.
     */
    interface Context {
        CompletableFuture<Void> enqueueWork(Runnable work);

        @Nullable
        Player sender();
    }
}
