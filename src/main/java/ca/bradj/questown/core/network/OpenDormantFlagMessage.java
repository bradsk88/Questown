package ca.bradj.questown.core.network;

import ca.bradj.questown.gui.ClientAccess;
import ca.bradj.questown.loader.Channel;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;

/**
 * Server → client: open the dormant-flag screen (wake the town, or get the deed back). Sent when
 * a player right-clicks a dormant flag with no deed waiting; the choice comes back as a
 * {@link DormantFlagChoiceMessage}.
 */
public record OpenDormantFlagMessage(BlockPos flagPos) implements ClientRunnable {

    public static void encode(
            OpenDormantFlagMessage msg,
            RegistryFriendlyByteBuf buffer
    ) {
        buffer.writeBlockPos(msg.flagPos());
    }

    public static OpenDormantFlagMessage decode(RegistryFriendlyByteBuf buffer) {
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
