package ca.bradj.questown.core.network;

import ca.bradj.questown.gui.ClientAccess;
import ca.bradj.questown.gui.UIJob;
import ca.bradj.questown.loader.Channel;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;

import java.util.UUID;

/** One skill's details for the skill tree's details screen (dev playtest 2026-10-29). */
public record ShowSkillDetailsMessage(
        UIJob job,
        BlockPos flagPos,
        UUID villager,
        boolean locked,
        /** The flag's Blocks of Progress when the screen was opened from the flag, else 0. */
        int flagBops
) implements ClientRunnable {

    public static void encode(
            ShowSkillDetailsMessage msg,
            FriendlyByteBuf buffer
    ) {
        UIJob.toNetwork(buffer, msg.job);
        buffer.writeBlockPos(msg.flagPos);
        buffer.writeUUID(msg.villager);
        buffer.writeBoolean(msg.locked);
        buffer.writeInt(msg.flagBops);
    }

    public static ShowSkillDetailsMessage decode(FriendlyByteBuf buffer) {
        return new ShowSkillDetailsMessage(
                UIJob.fromNetwork(buffer), buffer.readBlockPos(), buffer.readUUID(), buffer.readBoolean(), buffer.readInt()
        );
    }

    public void handle(
            Channel.Context ctx
    ) {
        ToClientMessage.handle(ctx, this);
    }

    @Override
    public void runOnClient() {
        ClientAccess.openSkillDetails(job, flagPos, villager, locked, flagBops);
    }
}
