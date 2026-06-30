package ca.bradj.questown.core.network;

import ca.bradj.questown.gui.ClientAccess;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Server → client: open the relocation "some of your town is too far away" confirmation screen
 * (ADR-0009, #199 Phase 4). Sent from {@code RelocationDeedItem.useOn} when a deed is placed at a
 * target that would leave some fixtures outside the new flag's tick radius. Carries the chosen target
 * and how many fixtures are out of range; the player's button choice comes back as a
 * {@link RelocationChoiceMessage}.
 */
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

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ToClientMessage.handle(ctx, this);
    }

    @Override
    public void runOnClient() {
        ClientAccess.openRelocationConfirm(targetPos, farFixtureCount);
    }
}
