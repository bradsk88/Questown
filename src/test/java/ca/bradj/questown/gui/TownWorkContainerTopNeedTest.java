package ca.bradj.questown.gui;

import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** The board's empty state gets the top missing item from the server. Only the string slot is tested here: the full round trip needs the item registry's network codec, which won't run outside a mod load. */
class TownWorkContainerTopNeedTest {

    @BeforeAll
    static void init() {
        net.minecraft.SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();
    }

    @Test
    void topNeedSlotRoundTripsThroughUtf() {
        // The slot the server writes and the client reads, after the two toggles.
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        buf.writeBoolean(false);
        buf.writeBoolean(true);
        buf.writeUtf("Wheat Seeds");

        assertEquals(false, buf.readBoolean());
        assertEquals(true, buf.readBoolean());
        assertEquals("Wheat Seeds", buf.readUtf());
    }

    @Test
    void emptyTopNeedSlotRoundTrips() {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        buf.writeBoolean(false);
        buf.writeBoolean(true);
        buf.writeUtf("");

        buf.readBoolean();
        buf.readBoolean();
        assertEquals("", buf.readUtf());
    }

    @Test
    void constructorNullBecomesEmptyString() {
        TownWorkContainer c = new TownWorkContainer(
                0,
                java.util.List.of(),
                new AddWorkContainer(0, com.google.common.collect.ImmutableList.of(), java.util.List.of(),
                        new net.minecraft.core.BlockPos(0, 0, 0)),
                new net.minecraft.core.BlockPos(0, 0, 0),
                false,
                true,
                null
        );
        assertEquals("", c.getTopUnmetNeedName());
    }

    @Test
    void constructorCarriesTheName() {
        TownWorkContainer c = new TownWorkContainer(
                0,
                java.util.List.of(),
                new AddWorkContainer(0, com.google.common.collect.ImmutableList.of(), java.util.List.of(),
                        new net.minecraft.core.BlockPos(0, 0, 0)),
                new net.minecraft.core.BlockPos(0, 0, 0),
                false,
                true,
                "Coal"
        );
        assertEquals("Coal", c.getTopUnmetNeedName());
    }
}
