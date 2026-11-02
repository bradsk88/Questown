package ca.bradj.questown.core.network;

import ca.bradj.questown.gui.ClientAccess;
import net.minecraft.core.BlockPos;
import ca.bradj.questown.loader.Channel;
import net.minecraft.network.FriendlyByteBuf;


/** Server to client: confirm moving a town when some of it would be left too far away. */
public record OpenRelocationConfirmMessage(
        BlockPos targetPos,
        int farFixtureCount
) implements ClientRunnable {

    public static void encode(
            OpenRelocationConfirmMessage msg,
            FriendlyByteBuf buffer
    ) {
        buffer.writeBlockPos(msg.targetPos());
        buffer.writeVarInt(msg.farFixtureCount());
    }

    public static OpenRelocationConfirmMessage decode(FriendlyByteBuf buffer) {
        BlockPos targetPos = buffer.readBlockPos();
        int farFixtureCount = buffer.readVarInt();
        return new OpenRelocationConfirmMessage(targetPos, farFixtureCount);
    }

    public void handle(Channel.Context ctx) {
        ToClientMessage.handle(ctx, this);
    }

    @Override
    public void runOnClient() {
        ClientAccess.openRelocationConfirm(targetPos, farFixtureCount);
    }
}
