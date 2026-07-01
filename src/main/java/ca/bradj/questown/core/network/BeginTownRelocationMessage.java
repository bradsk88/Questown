package ca.bradj.questown.core.network;

import ca.bradj.questown.town.entity.TownFlagBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
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
            sender.displayClientMessage(
                    Component.translatable(started
                            ? "message.questown.relocation.shutdown_started"
                            : "message.questown.relocation.not_eligible"),
                    true
            );
        });
        ctx.get().setPacketHandled(true);
    }
}
