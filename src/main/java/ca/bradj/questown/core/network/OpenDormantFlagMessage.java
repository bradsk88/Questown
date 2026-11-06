package ca.bradj.questown.core.network;

import ca.bradj.questown.gui.ClientAccess;
import ca.bradj.questown.loader.Channel;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;

/** Server to client: open the dormant-flag screen (wake the town, or get the deed back). */
public record OpenDormantFlagMessage(BlockPos flagPos) implements ClientRunnable {

    public static void encode(
            OpenDormantFlagMessage msg,
            FriendlyByteBuf buffer
    ) {
        buffer.writeBlockPos(msg.flagPos());
    }

    public static OpenDormantFlagMessage decode(FriendlyByteBuf buffer) {
        return new OpenDormantFlagMessage(buffer.readBlockPos());
    }

    public void handle(Channel.Context ctx) {
        ToClientMessage.handle(ctx, this);
    }

    @Override
    public void runOnClient() {
        ClientAccess.openDormantFlag(flagPos);
    }
}
