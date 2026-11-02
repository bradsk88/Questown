package ca.bradj.questown.loader.neo;

import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.Optional;

/** The one payload type every Questown message travels in: the message id, plus the decoded message on arrival. */
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
