package ca.bradj.questown.core.network;

import ca.bradj.questown.QT;
import ca.bradj.questown.items.RelocationDeedItem;
import ca.bradj.questown.town.entity.TownRelocation;
import ca.bradj.questown.town.entity.TownRelocation.FarFixturePolicy;
import ca.bradj.questown.town.entity.TownRelocation.RelocationResult;
import net.minecraft.core.BlockPos;
import ca.bradj.questown.loader.Channel;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;


/**
 * Client → server: the player's answer to the relocation "too far away" confirmation screen
 * (ADR-0009, #199 Phase 4). The deed has stayed in hand since {@code RelocationDeedItem.useOn}
 * deferred to the screen; this finishes the placement with the chosen {@link FarFixturePolicy} and
 * consumes the deed only on success. Cancel sends no message at all — the screen just closes, leaving
 * the deed untouched.
 */
public record RelocationChoiceMessage(
        BlockPos targetPos,
        boolean leaveBehind
) {

    public static void encode(
            RelocationChoiceMessage msg,
            RegistryFriendlyByteBuf buffer
    ) {
        buffer.writeBlockPos(msg.targetPos());
        buffer.writeBoolean(msg.leaveBehind());
    }

    public static RelocationChoiceMessage decode(RegistryFriendlyByteBuf buffer) {
        BlockPos targetPos = buffer.readBlockPos();
        boolean leaveBehind = buffer.readBoolean();
        return new RelocationChoiceMessage(targetPos, leaveBehind);
    }

    public void handle(Channel.Context ctx) {
        ctx.enqueueWork(() -> {
            ServerPlayer sender = (ServerPlayer) ctx.sender();
            if (sender == null) {
                return;
            }
            ServerLevel level = sender.serverLevel();
            InteractionHand hand = deedHand(sender);
            if (hand == null) {
                QT.GUI_LOGGER.warn("Relocation choice arrived but the player no longer holds a deed");
                return;
            }
            ItemStack deed = sender.getItemInHand(hand);
            FarFixturePolicy policy = leaveBehind ? FarFixturePolicy.LEAVE_BEHIND : FarFixturePolicy.BRING_ALL;
            RelocationResult result = TownRelocation.place(level, deed, targetPos, policy);
            if (result != RelocationResult.OK) {
                sender.displayClientMessage(RelocationDeedItem.messageFor(result), true);
                return;
            }
            RelocationDeedItem.consumeFrom(sender, hand);
        });
    }

    private static InteractionHand deedHand(ServerPlayer player) {
        for (InteractionHand hand : InteractionHand.values()) {
            if (RelocationDeedItem.isDeed(player.getItemInHand(hand))) {
                return hand;
            }
        }
        return null;
    }
}
