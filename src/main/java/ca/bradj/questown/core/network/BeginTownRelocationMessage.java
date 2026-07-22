package ca.bradj.questown.core.network;

import ca.bradj.questown.mc.Compat;
import ca.bradj.questown.town.entity.TownFlagBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Client → server: the flag menu's "Begin moving this town" action (ADR-0009, #199). The
 * deed-independent, in-game entry point that wraps {@link TownFlagBlockEntity#beginTownShutdown()} —
 * the same ritual the {@code /qt flag shutdown <pos>} command triggers. Eligibility (flag must be
 * {@link ca.bradj.questown.blocks.FlagPhase#ACTIVE}) is decided server-side by
 * {@code beginTownShutdown()}; the player gets an action-bar acknowledgement either way.
 */
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

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer sender = ctx.get().getSender();
            if (sender == null) {
                return;
            }
            if (!(sender.getLevel().getBlockEntity(flagPos) instanceof TownFlagBlockEntity flag)) {
                return;
            }
            boolean started = flag.beginTownShutdown();
            // Send as chat (not an action-bar overlay): the message is long, so the overlay
            // truncated it off-screen and faded before it could be read.
            sender.sendSystemMessage(Compat.translatable(startMessageKey(started, flag)));
        });
        ctx.get().setPacketHandled(true);
    }

    private static String startMessageKey(boolean started, TownFlagBlockEntity flag) {
        if (!started) {
            return "message.questown.relocation.not_eligible";
        }
        // With no villagers there is no one to gather/recall — the deed just appears after the
        // minimum-duration floor, so the "townsfolk are gathering" copy would be nonsense.
        if (flag.getVillagerHandle().size() == 0) {
            return "message.questown.relocation.shutdown_started_no_villagers";
        }
        return "message.questown.relocation.shutdown_started";
    }
}
