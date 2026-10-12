package ca.bradj.questown.gui;

import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * job-board-empty-state.md: the board's empty state points at the flag's top unmet need. The
 * need name travels from the server's menu-open to the client through the container's
 * serialization. The full ForClientSide round trip needs the work-results write, which pushes
 * Ingredients through Neo's item-registry ID codec - that codec refuses a registry Neo has not
 * marked synced, and vanilla's BuiltInRegistries.ITEM is never marked synced outside a running
 * mod-load (Neo's registry lifecycle), so it cannot be exercised in a plain unit test.
 * What IS testable here: the new writeUtf/readUtf slot the round trip depends on, and the
 * constructor's null-contract.
 *
 * The full server->client path (TownWorkHandle writing and ForClientSide reading the same Utf
 * slot) is covered by the held playtest: open the job board with no requests and a townie with
 * an unmet need, and read the empty-state line.
 */
class TownWorkContainerTopNeedTest {

    @BeforeAll
    static void init() {
        net.minecraft.SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();
    }

    @Test
    void topNeedSlotRoundTripsThroughUtf() {
        // The exact slot TownWorkHandle writes and ForClientSide reads: a trailing Utf after
        // the two toggle booleans.
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        buf.writeBoolean(false); // skipToAdd
        buf.writeBoolean(true);  // boardAutoRequest
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
                new AddWorkContainer(0, com.google.common.collect.ImmutableList.of(),
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
                new AddWorkContainer(0, com.google.common.collect.ImmutableList.of(),
                        new net.minecraft.core.BlockPos(0, 0, 0)),
                new net.minecraft.core.BlockPos(0, 0, 0),
                false,
                true,
                "Coal"
        );
        assertEquals("Coal", c.getTopUnmetNeedName());
    }
}
