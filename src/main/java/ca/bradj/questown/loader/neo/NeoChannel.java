package ca.bradj.questown.loader.neo;

import ca.bradj.questown.QT;
import ca.bradj.questown.loader.Channel;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.common.extensions.ICommonPacketListener;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadHandler;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

/** The NeoForge {@link Channel}: one payload type carries every message, tagged with its id, and is registered for both directions. */
public final class NeoChannel implements Channel {

    private final String channelId;
    private final List<Entry> entries = new CopyOnWriteArrayList<>();
    private final Map<Class<?>, Entry> byClass = new ConcurrentHashMap<>();
    private final Map<Integer, Entry> byId = new ConcurrentHashMap<>();
    private final AtomicInteger nextId = new AtomicInteger(0);

    NeoChannel(String channelId) {
        this.channelId = channelId;
    }

    @Override
    public <T> Builder<T> message(Class<T> type, Direction dir) {
        Entry e = new Entry(nextId.getAndIncrement(), type);
        if (byClass.putIfAbsent(type, e) != null) {
            throw new IllegalStateException("message type already registered: " + type.getName());
        }
        byId.put(e.id, e);
        entries.add(e);
        return new BuilderImpl<>(type);
    }

    @Override
    public <T> void sendToPlayer(Player p, T msg) {
        if (p instanceof ServerPlayer sp) {
            ((ICommonPacketListener) sp.connection).send(wrap(msg));
        } else {
            QT.LOGGER.warn("[questown] sendToPlayer dropped (not a server player): {}", msg);
        }
    }

    @Override
    public <T> void sendToAll(Level level, T msg) {
        if (level instanceof ServerLevel sl) {
            for (ServerPlayer player : sl.players()) {
                sendToPlayer(player, msg);
            }
        } else {
            QT.LOGGER.warn("[questown] sendToAll dropped (not a server level): {}", msg);
        }
    }

    private java.util.function.Consumer<NeoEnvelope> clientSend;

    public void setClientSend(java.util.function.Consumer<NeoEnvelope> hook) {
        this.clientSend = hook;
    }

    @Override
    public <T> void sendToServer(T msg) {
        java.util.function.Consumer<NeoEnvelope> hook = clientSend;
        if (hook == null) {
            QT.LOGGER.warn("[questown] sendToServer dropped (no client send hook installed)");
            return;
        }
        hook.accept(wrap(msg));
    }

    private NeoEnvelope wrap(Object msg) {
        Entry entry = byClass.get(msg.getClass());
        if (entry == null) {
            throw new IllegalStateException("send for unregistered message: " + msg.getClass().getName());
        }
        return new NeoEnvelope(entry.id, msg);
    }

    void onRegisterPayloadHandlers(RegisterPayloadHandlersEvent event) {
        for (Entry e : entries) {
            if (e.encoder == null || e.decoder == null || e.handler == null) {
                throw new IllegalStateException("incomplete message registration for " + e.type.getName()
                        + " (encoder/decoder/handler all required before RegisterPayloadHandlersEvent)");
            }
        }
        PayloadRegistrar reg = event.registrar(channelId);
        // One registration for both directions; registering each direction separately throws.
        reg.playBidirectional(NeoEnvelope.TYPE, wireCodec(), payloadHandler());
        QT.LOGGER.info("[questown] registered {} message types on the Neo payload channel '{}'", entries.size(), channelId);
    }

    /** On the wire: the message id as a varint, then the message's own bytes. */
    public StreamCodec<RegistryFriendlyByteBuf, NeoEnvelope> wireCodec() {
        return StreamCodec.of(
                (buf, env) -> {
                    buf.writeVarInt(env.id());
                    entryFor(env.id()).encoder().encode(env.msg().orElseThrow(), buf);
                },
                buf -> {
                    Entry entry = entryFor(buf.readVarInt());
                    return new NeoEnvelope(entry.id, entry.decoder().decode(buf));
                }
        );
    }

    /** Hands each arriving envelope to its message's handler. */
    public IPayloadHandler<NeoEnvelope> payloadHandler() {
        return (env, ctx) -> {
            Object msg = env.msg().orElse(null);
            if (msg == null) {
                return;
            }
            entryFor(env.id()).handler().handle(msg, new NeoPayloadContext(ctx));
        };
    }

    private Entry entryFor(int id) {
        Entry entry = byId.get(id);
        if (entry == null) {
            throw new IllegalStateException("no message registered under id " + id
                    + " (both sides must run the same Questown version)");
        }
        return entry;
    }

    private final class BuilderImpl<T> implements Builder<T> {
        private final Class<T> type;

        BuilderImpl(Class<T> type) {
            this.type = type;
        }

        @Override
        public Builder<T> encoder(Encoder<T> encoder) {
            entry().encoder = encoder;
            return this;
        }

        @Override
        public Builder<T> decoder(Decoder<T> decoder) {
            entry().decoder = decoder;
            return this;
        }

        @Override
        public Builder<T> handler(Handler<T> handler) {
            entry().handler = handler;
            return this;
        }

        private Entry entry() {
            Entry entry = byClass.get(type);
            if (entry == null) {
                throw new IllegalStateException("message not registered: " + type.getName());
            }
            return entry;
        }
    }

    private static final class Entry {
        final int id;
        final Class<?> type;
        Channel.Encoder<?> encoder;
        Channel.Decoder<?> decoder;
        Channel.Handler<?> handler;

        Entry(int id, Class<?> type) {
            this.id = id;
            this.type = type;
        }

        @SuppressWarnings("unchecked")
        Channel.Encoder<Object> encoder() {
            return (Channel.Encoder<Object>) encoder;
        }

        @SuppressWarnings("unchecked")
        Channel.Decoder<Object> decoder() {
            return (Channel.Decoder<Object>) decoder;
        }

        @SuppressWarnings("unchecked")
        Channel.Handler<Object> handler() {
            return (Channel.Handler<Object>) handler;
        }
    }
}
