package ca.bradj.questown.core.network;

import ca.bradj.questown.QT;
import ca.bradj.questown.town.entity.TownFlagBlockEntity;
import ca.bradj.questown.loader.Channel;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;

/**
 * The one-time free-food claim on the economics tab's food card
 * (.scratch/day1-playtest-2026-10-11/free-food-economics-card.md). Replaces
 * the old "right-click the flag with a block of dirt" trigger: the owner of
 * the action is {@link TownFlagBlockEntity#claimBonusFood}.
 */
public record ClaimFreeFoodMessage(BlockPos flagPos) {

    public static void encode(
            ClaimFreeFoodMessage msg,
            RegistryFriendlyByteBuf buffer
    ) {
        buffer.writeBlockPos(msg.flagPos);
    }

    public static ClaimFreeFoodMessage decode(RegistryFriendlyByteBuf buffer) {
        return new ClaimFreeFoodMessage(buffer.readBlockPos());
    }

    public void handle(
            Channel.Context ctx
    ) {
        ctx.enqueueWork(() -> {
            ServerPlayer sender = (ServerPlayer) ctx.sender();
            if (!(sender.level().getBlockEntity(flagPos) instanceof TownFlagBlockEntity flag)) {
                QT.GUI_LOGGER.error("No flag at position {}. Free food cannot be claimed.", flagPos);
                return;
            }
            flag.claimBonusFood(sender);
        });
    }
}
