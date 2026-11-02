package ca.bradj.questown.core.network;

import ca.bradj.questown.mc.Compat;
import ca.bradj.questown.town.entity.TownFlagBlockEntity;
import net.minecraft.core.BlockPos;
import ca.bradj.questown.loader.Channel;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;


/** Client to server: the flag menu's "Begin moving this town". */
public record BeginTownRelocationMessage(BlockPos flagPos) {

    public static void encode(
            BeginTownRelocationMessage msg,
            FriendlyByteBuf buffer
    ) {
        buffer.writeBlockPos(msg.flagPos());
    }

    public static BeginTownRelocationMessage decode(FriendlyByteBuf buffer) {
        return new BeginTownRelocationMessage(buffer.readBlockPos());
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
            boolean started = flag.beginTownShutdown();
            // Chat, not the action bar: the message is too long for it.
            sender.sendSystemMessage(Compat.translatable(startMessageKey(started, flag)));
        });
    }

    private static String startMessageKey(boolean started, TownFlagBlockEntity flag) {
        if (!started) {
            return "message.questown.relocation.not_eligible";
        }
        // With no villagers, nobody gathers; the deed just appears.
        if (flag.getVillagerHandle().size() == 0) {
            return "message.questown.relocation.shutdown_started_no_villagers";
        }
        return "message.questown.relocation.shutdown_started";
    }
}
