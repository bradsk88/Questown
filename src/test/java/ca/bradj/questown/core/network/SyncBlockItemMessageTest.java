package ca.bradj.questown.core.network;

import io.netty.buffer.Unpooled;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Agent playtest r12 #10: placing an empty plate or food display synced an empty stack through
 * the non-optional ItemStack codec ("Empty ItemStack not allowed"), which dropped the player's
 * connection, and every rejoin re-sent it.
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
        RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);

        SyncBlockItemMessage.encode(msg, buf);
        SyncBlockItemMessage back = SyncBlockItemMessage.decode(buf);

        Assertions.assertEquals(msg.pos(), back.pos());
        Assertions.assertTrue(back.item().isEmpty());
        Assertions.assertEquals(0, back.index());
    }
}
