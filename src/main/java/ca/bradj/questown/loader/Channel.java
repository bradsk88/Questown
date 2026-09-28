package ca.bradj.questown.loader;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.CompletableFuture;

/**
 * Neutral message channel (Forge {@code SimpleChannel} / NeoForge payload-handler equivalent).
 * Main code registers a message type with its wire encoder/decoder over the vanilla
 * {@link RegistryFriendlyByteBuf} plus a handler, then sends via
 * {@link #sendToPlayer}/{@link #sendToAll}/{@link #sendToServer}. The active loader adapts this to
 * its own stack (NeoForge's Payload API: one envelope payload, a StreamCodec over the registered
 * encoder/decoders, and one IPayloadHandler per direction), so the Questown message classes (which
 * are already vanilla-buffer POJOs) stay neutral.
 *
 * <p>A {@link #message(Class, Direction)} registration is finalised by {@code handler(...)}; the
 * encoder and decoder must both be present (the message's {@code encode}/{@code decode} statics).
 */
public interface Channel {

    enum Direction { TO_CLIENT, TO_SERVER }

    <T> Builder<T> message(Class<T> type, Direction dir);

    /** Server to one player. */
    <T> void sendToPlayer(Player p, T msg);

    /** Server to every player in the level's dimension (the former {@code PacketDistributor.ALL}). */
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
        void encode(T msg, RegistryFriendlyByteBuf buf);
    }

    @FunctionalInterface
    interface Decoder<T> {
        T decode(RegistryFriendlyByteBuf buf);
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
