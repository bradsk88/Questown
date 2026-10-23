package ca.bradj.questown.core.network;

import ca.bradj.questown.loader.Channel;
import ca.bradj.questown.mc.Compat;
import ca.bradj.questown.town.entity.TownFlagBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;

/**
 * Client → server: the flag menu's "Cancel moving" during the shutdown ritual. Wraps
 * {@link TownFlagBlockEntity#cancelTownShutdown()}, the same path as
 * {@code /qt flag <pos> shutdown cancel}: the townies absorbed so far come back and the flag
 * returns to ACTIVE.
 */
public record CancelTownShutdownMessage(BlockPos flagPos) {

    public static void encode(
            CancelTownShutdownMessage msg,
            RegistryFriendlyByteBuf buffer
    ) {
        buffer.writeBlockPos(msg.flagPos());
    }

    public static CancelTownShutdownMessage decode(RegistryFriendlyByteBuf buffer) {
        return new CancelTownShutdownMessage(buffer.readBlockPos());
    }

    public void handle(Channel.Context ctx) {
        ctx.enqueueWork(() -> {
            ServerPlayer sender = (ServerPlayer) ctx.sender();
            if (sender == null) {
                return;
            }
            if (!(sender.level().getBlockEntity(flagPos) instanceof TownFlagBlockEntity flag)) {
                return;
            }
            boolean cancelled = flag.cancelTownShutdown();
            sender.sendSystemMessage(Compat.translatable(cancelled
                    ? "message.questown.relocation.cancelled"
                    : "message.questown.relocation.cancel_not_eligible"));
        });
    }
}
