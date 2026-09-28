package ca.bradj.questown.loader.neo;

import ca.bradj.questown.core.network.CloseScreensMessage;
import ca.bradj.questown.core.network.OnScreenTextMessage;
import ca.bradj.questown.loader.Channel;
import io.netty.buffer.Unpooled;
import net.minecraft.SharedConstants;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Wire-format round trip through the NeoForge envelope codec: the wire starts with the varint
 * message id and then the message's own encoder bytes; both directions must agree on that, or a
 * correct encode produces a decode that reads the message's own first bytes as the id.
 */
class NeoChannelWireTest {

    private static final RegistryAccess ACCESS = RegistryAccess.EMPTY;

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    private static NeoChannel channel() {
        NeoChannel ch = new NeoChannel("questown:test");
        ch.message(CloseScreensMessage.class, Channel.Direction.TO_CLIENT)
                .encoder(CloseScreensMessage::encode)
                .decoder(CloseScreensMessage::decode)
                .handler((msg, ctx) -> {});
        ch.message(OnScreenTextMessage.class, Channel.Direction.TO_CLIENT)
                .encoder(OnScreenTextMessage::encode)
                .decoder(OnScreenTextMessage::decode)
                .handler((msg, ctx) -> {});
        return ch;
    }

    private static NeoEnvelope roundTrip(NeoChannel ch, NeoEnvelope env) {
        RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer(1024), ACCESS);
        ch.wireCodec().encode(buf, env);
        int len = buf.writerIndex();
        byte[] wire = new byte[len];
        buf.getBytes(0, wire);
        RegistryFriendlyByteBuf in = new RegistryFriendlyByteBuf(Unpooled.wrappedBuffer(wire), ACCESS);
        return ch.wireCodec().decode(in);
    }

    @Test
    void envelope_withBody_roundTrips_idAndMessage() {
        NeoChannel ch = channel();
        OnScreenTextMessage msg = new OnScreenTextMessage("quest.toast", "arg1", "arg2");
        NeoEnvelope back = roundTrip(ch, new NeoEnvelope(1, msg));
        assertEquals(1, back.id());
        assertTrue(back.msg().isPresent());
        OnScreenTextMessage decoded = (OnScreenTextMessage) back.msg().orElseThrow();
        assertEquals(msg.key(), decoded.key());
        assertTrue(Arrays.equals(msg.args(), decoded.args()), "body fields must survive the wire");
    }

    @Test
    void envelope_emptyBody_roundTrips_idAndMessage() {
        // An empty body is the sharpest case: the wire is the id itself. An encode that does not
        // write the id yields an empty wire, so the decode's first readVarInt fails or misroutes.
        NeoChannel ch = channel();
        NeoEnvelope back = roundTrip(ch, new NeoEnvelope(0, new CloseScreensMessage()));
        assertEquals(0, back.id());
        assertTrue(back.msg().isPresent());
        assertSame(CloseScreensMessage.class, back.msg().orElseThrow().getClass());
    }

    @Test
    void envelope_idsStayDistinctAcrossMessages() {
        NeoChannel ch = channel();
        // Two distinct ids for two distinct messages; a wire that drops the id cannot survive this.
        NeoEnvelope a = roundTrip(ch, new NeoEnvelope(0, new CloseScreensMessage()));
        NeoEnvelope b = roundTrip(ch, new NeoEnvelope(1, new OnScreenTextMessage("k", "x")));
        assertEquals(0, a.id());
        assertEquals(1, b.id());
        assertSame(OnScreenTextMessage.class, b.msg().orElseThrow().getClass());
    }
}
