package ca.bradj.questown.loader;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

/**
 * Neutral message channel (Forge {@code SimpleChannel} equivalent). Main code registers a message type with
 * its wire encoder/decoder over the vanilla {@link RegistryFriendlyByteBuf} plus a handler, then sends via
 * {@link #sendToServer}/{@link #sendToPlayer}/{@link #sendToTracking}. The active loader adapts this to its
 * own stack (NeoForge's Payload API), so the 31 Questown message classes (which are already vanilla-buffer
 * POJOs) stay neutral.
 */
public interface Channel {

    enum Direction { TO_CLIENT, TO_SERVER }

    <T> MessageBuilder<T> messageBuilder(Class<T> type, Direction dir);

    <T> void sendToServer(T msg);

    <T> void sendToPlayer(Player p, T msg);

    <T> void sendToTracking(Entity entity, T msg);

    interface MessageBuilder<T> {
        MessageBuilder<T> encoder(Encoder<T> encoder);
        MessageBuilder<T> decoder(Decoder<T> decoder);
        void add(Handler<T> handler);
    }

    @FunctionalInterface
    interface Encoder<T> {
        void encode(T msg, RegistryFriendlyByteBuf buf);
    }

    @FunctionalInterface
    interface Decoder<T> {
        T decode(RegistryFriendlyByteBuf buf);
    }

    @FunctionalInterface
    interface Handler<T> {
        void handle(T msg, Player player);
    }
}
