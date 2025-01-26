package ca.bradj.questown.core.network;

import ca.bradj.questown.gui.villager.advancements.VillagerAdvancements;
import ca.bradj.questown.jobs.JobID;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkEvent;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.function.Supplier;

public record SyncVillagerAdvancementsMessage(
        Map<JobID, @Nullable JobID> parents,
        Map<JobID, ResourceLocation> icons
) implements ClientRunnable {

    public static void encode(
            SyncVillagerAdvancementsMessage msg,
            FriendlyByteBuf buffer
    ) {
        buffer.writeMap(msg.parents, NetworkCompat::toNetwork, NetworkCompat::toNetworkNullable);
        buffer.writeMap(msg.icons, NetworkCompat::toNetwork, FriendlyByteBuf::writeResourceLocation);
    }

    public static SyncVillagerAdvancementsMessage decode(FriendlyByteBuf buffer) {
        Map<JobID, @Nullable JobID> jobs = buffer.readMap(
                NetworkCompat::fromNetworkJobID,
                NetworkCompat::fromNetworkJobIDNullable
        );

        Map<JobID, ResourceLocation> icons = buffer.readMap(
                NetworkCompat::fromNetworkJobID,
                FriendlyByteBuf::readResourceLocation
        );

        return new SyncVillagerAdvancementsMessage(jobs, icons);
    }

    public void handle(
            Supplier<NetworkEvent.Context> ctx
    ) {
        ToClientMessage.handle(ctx, this);
    }

    @Override
    public void runOnClient() {
        parents.forEach(VillagerAdvancements::registerOnClientSide);
        VillagerAdvancements.registerIcons(icons);
    }
}
