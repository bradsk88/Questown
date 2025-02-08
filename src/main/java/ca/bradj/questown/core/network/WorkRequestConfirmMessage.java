package ca.bradj.questown.core.network;

import ca.bradj.questown.gui.ClientAccess;
import ca.bradj.questown.gui.Ingredients;
import ca.bradj.questown.jobs.JobID;
import com.google.common.collect.ImmutableMap;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraftforge.network.NetworkEvent;

import java.util.Map;
import java.util.function.Supplier;

public record WorkRequestConfirmMessage(
        Ingredient itemRequested,
        Map<JobID, ResourceLocation> iconsForJobsWhichProduceResult,
        BlockPos flagPos
) implements ClientRunnable {

    public static void encode(
            WorkRequestConfirmMessage msg,
            FriendlyByteBuf buffer
    ) {
        Ingredients.toNetwork(msg.itemRequested(), buffer);
        buffer.writeMap(
                msg.iconsForJobsWhichProduceResult(),
                NetworkCompat::toNetwork,
                FriendlyByteBuf::writeResourceLocation
        );
        buffer.writeBlockPos(msg.flagPos());
    }

    public static WorkRequestConfirmMessage decode(FriendlyByteBuf buffer) {
        Ingredient itemRequested = Ingredients.fromNetwork(buffer);
        Map<JobID, ResourceLocation> l = buffer.readMap(
                NetworkCompat::fromNetworkJobID,
                FriendlyByteBuf::readResourceLocation
        );
        BlockPos flagPos = buffer.readBlockPos();
        return new WorkRequestConfirmMessage(itemRequested, ImmutableMap.copyOf(l), flagPos);
    }

    public void handle(
            Supplier<NetworkEvent.Context> ctx
    ) {
        ToClientMessage.handle(ctx, this);
    }

    @Override
    public void runOnClient() {
        ClientAccess.openWorkRequestConfirm(itemRequested, iconsForJobsWhichProduceResult, flagPos);
    }
}
