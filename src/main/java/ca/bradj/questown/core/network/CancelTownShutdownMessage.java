package ca.bradj.questown.core.network;

import ca.bradj.questown.loader.Channel;
import ca.bradj.questown.mc.Compat;
import ca.bradj.questown.town.entity.TownFlagBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;

public record CancelTownShutdownMessage(BlockPos flagPos) {

    public static void encode(
            CancelTownShutdownMessage msg,
            FriendlyByteBuf buffer
    ) {
        buffer.writeBlockPos(msg.flagPos());
    }

    public static CancelTownShutdownMessage decode(FriendlyByteBuf buffer) {
        return new CancelTownShutdownMessage(buffer.readBlockPos());
    }

    public void handle(Channel.Context ctx) {
        ctx.enqueueWork(() -> {
            ServerPlayer sender = (ServerPlayer) ctx.sender();
            if (sender == null) {
                return;
            }
            if (!(sender.getLevel().getBlockEntity(flagPos) instanceof TownFlagBlockEntity flag)) {
                return;
            }
            boolean cancelled = flag.cancelTownShutdown();
            sender.sendSystemMessage(Compat.translatable(cancelled
                    ? "message.questown.relocation.cancelled"
                    : "message.questown.relocation.cancel_not_eligible"));
        });
    }
}
