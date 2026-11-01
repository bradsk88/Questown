package ca.bradj.questown.core.network;

import ca.bradj.questown.QT;
import ca.bradj.questown.town.entity.TownFlagBlockEntity;
import ca.bradj.questown.loader.Channel;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;

/** The economics tab's one-time free-food claim: {@link TownFlagBlockEntity#claimBonusFood}. */
public record ClaimFreeFoodMessage(BlockPos flagPos) {

    public static void encode(
            ClaimFreeFoodMessage msg,
            FriendlyByteBuf buffer
    ) {
        buffer.writeBlockPos(msg.flagPos);
    }

    public static ClaimFreeFoodMessage decode(FriendlyByteBuf buffer) {
        return new ClaimFreeFoodMessage(buffer.readBlockPos());
    }

    public void handle(
            Channel.Context ctx
    ) {
        ctx.enqueueWork(() -> {
            ServerPlayer sender = (ServerPlayer) ctx.sender();
            if (!(sender.getLevel().getBlockEntity(flagPos) instanceof TownFlagBlockEntity flag)) {
                QT.GUI_LOGGER.error("No flag at position {}. Free food cannot be claimed.", flagPos);
                return;
            }
            flag.claimBonusFood(sender);
        });
    }
}
