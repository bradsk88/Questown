package ca.bradj.questown.core.network;

import ca.bradj.questown.loader.Channel;
import ca.bradj.questown.mc.Compat;
import ca.bradj.questown.town.entity.TownFlagBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;

/**
 * Client → server: the dormant-flag screen's choice. {@code wake} wakes the town in place
 * ({@link TownFlagBlockEntity#wakeInPlace()}); otherwise the deed is re-issued above the flag
 * ({@link TownFlagBlockEntity#reissueDeed()}). Both are only valid while DORMANT; the owner
 * decides, and the player gets a chat line either way.
 */
public record DormantFlagChoiceMessage(BlockPos flagPos, boolean wake) {

    public static void encode(
            DormantFlagChoiceMessage msg,
            RegistryFriendlyByteBuf buffer
    ) {
        buffer.writeBlockPos(msg.flagPos());
        buffer.writeBoolean(msg.wake());
    }

    public static DormantFlagChoiceMessage decode(RegistryFriendlyByteBuf buffer) {
        BlockPos flagPos = buffer.readBlockPos();
        boolean wake = buffer.readBoolean();
        return new DormantFlagChoiceMessage(flagPos, wake);
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
            boolean ok = wake ? flag.wakeInPlace() : flag.reissueDeed();
            sender.sendSystemMessage(Compat.translatable(messageKey(wake, ok)));
        });
    }

    private static String messageKey(boolean wake, boolean ok) {
        if (!ok) {
            return "message.questown.relocation.dormant_not_eligible";
        }
        return wake ? "message.questown.relocation.woken" : "message.questown.relocation.deed_reissued";
    }
}
