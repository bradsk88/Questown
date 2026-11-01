package ca.bradj.questown.core.network;

import io.netty.buffer.Unpooled;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * An empty plate or food display syncs an empty stack; a non-optional item codec rejects it and
 * drops the player's connection.
 */
class SyncBlockItemMessageTest {

    @BeforeAll
    static void boot() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void anEmptySlot_roundTripsOverTheWire() {
        SyncBlockItemMessage msg = new SyncBlockItemMessage(new BlockPos(-18, 76, 199), ItemStack.EMPTY, 0);
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());

        SyncBlockItemMessage.encode(msg, buf);
        SyncBlockItemMessage back = SyncBlockItemMessage.decode(buf);

        Assertions.assertEquals(msg.pos(), back.pos());
        Assertions.assertTrue(back.item().isEmpty());
        Assertions.assertEquals(0, back.index());
    }
}
