package ca.bradj.questown.loader.neo;

import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.Optional;

/**
 * The one wire type the NeoForge impl registers for all Questown messages. It carries the
 * per-message id (assigned by {@link NeoChannel#message}) plus the decoded neutral message POJO on
 * the receiving side; the wire format (varint id + the message's neutral encoder bytes) is built by
 * the StreamCodec in {@link NeoChannel}.
 */
public final class NeoEnvelope implements CustomPacketPayload {

    public static final Type<NeoEnvelope> TYPE = new Type<>(new ResourceLocation("questown", "network_message"));

    private final int id;
    private final Object msg;

    NeoEnvelope(int id, Object msg) {
        this.id = id;
        this.msg = msg;
    }

    public Type<NeoEnvelope> type() {
        return TYPE;
    }

    public int id() {
        return id;
    }

    public Optional<Object> msg() {
        return Optional.ofNullable(msg);
    }
}
