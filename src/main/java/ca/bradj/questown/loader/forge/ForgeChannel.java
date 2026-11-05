package ca.bradj.questown.loader.forge;

import ca.bradj.questown.QT;
import ca.bradj.questown.loader.Channel;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

/**
 * Forge 1.19.2 {@link Channel}: one {@link SimpleChannel}. A message registers with Forge when its
 * builder's last call ({@code handler}) arrives; ids follow registration order, so both sides
 * must run the same Questown version (the protocol string guards the handshake).
 */
public final class ForgeChannel implements Channel {

    private static final String PROTOCOL = "1";

    private final SimpleChannel channel;
    private final AtomicInteger nextId = new AtomicInteger(0);

    ForgeChannel(String channelId) {
        this.channel = NetworkRegistry.newSimpleChannel(
                new ResourceLocation(channelId), () -> PROTOCOL, PROTOCOL::equals, PROTOCOL::equals);
    }

    @Override
    public <T> Builder<T> message(Class<T> type, Direction dir) {
        return new BuilderImpl<>(type, dir);
    }

    @Override
    public <T> void sendToPlayer(Player p, T msg) {
        if (p instanceof ServerPlayer sp) {
            channel.send(PacketDistributor.PLAYER.with(() -> sp), msg);
        } else {
            QT.LOGGER.warn("[questown] sendToPlayer dropped (not a server player): {}", msg);
        }
    }

    @Override
    public <T> void sendToAll(Level level, T msg) {
        if (level instanceof ServerLevel sl) {
            channel.send(PacketDistributor.DIMENSION.with(sl::dimension), msg);
        } else {
            QT.LOGGER.warn("[questown] sendToAll dropped (not a server level): {}", msg);
        }
    }

    @Override
    public <T> void sendToServer(T msg) {
        channel.sendToServer(msg);
    }

    private final class BuilderImpl<T> implements Builder<T> {
        private final Class<T> type;
        private final Direction dir;
        private Encoder<T> encoder;
        private Decoder<T> decoder;

        BuilderImpl(Class<T> type, Direction dir) {
            this.type = type;
            this.dir = dir;
        }

        @Override
        public Builder<T> encoder(Encoder<T> encoder) {
            this.encoder = encoder;
            return this;
        }

        @Override
        public Builder<T> decoder(Decoder<T> decoder) {
            this.decoder = decoder;
            return this;
        }

        @Override
        public Builder<T> handler(Handler<T> handler) {
            if (encoder == null || decoder == null) {
                throw new IllegalStateException("incomplete message registration for " + type.getName()
                        + " (encoder and decoder must come before the handler)");
            }
            NetworkDirection nd = dir == Direction.TO_CLIENT
                    ? NetworkDirection.PLAY_TO_CLIENT
                    : NetworkDirection.PLAY_TO_SERVER;
            Encoder<T> enc = encoder;
            Decoder<T> dec = decoder;
            channel.messageBuilder(type, nextId.getAndIncrement(), nd)
                    .encoder(enc::encode)
                    .decoder(dec::decode)
                    .consumerNetworkThread((T msg, Supplier<NetworkEvent.Context> ctx) -> {
                        handler.handle(msg, new Ctx(ctx.get(), dir));
                        ctx.get().setPacketHandled(true);
                    })
                    .add();
            return this;
        }
    }

    private record Ctx(NetworkEvent.Context delegate, Direction dir) implements Channel.Context {
        @Override
        public CompletableFuture<Void> enqueueWork(Runnable work) {
            return delegate.enqueueWork(work);
        }

        @Override
        public @Nullable Player sender() {
            if (dir == Direction.TO_SERVER) {
                return delegate.getSender();
            }
            // To the client: the neutral contract hands back the local player.
            return DistExecutor.unsafeCallWhenOn(Dist.CLIENT, () -> ForgeLoaderClient::localPlayer);
        }
    }

}
