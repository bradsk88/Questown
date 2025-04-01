package ca.bradj.questown.core.network;

import ca.bradj.questown.gui.ClientAccess;
import ca.bradj.questown.jobs.JobID;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.Collection;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;

public final class OpenVillagerAdvancementsMenuMessage {
    private final BlockPos flagPos;
    private final UUID villagerUUID;
    private final Collection<JobID> unlockedJobs;
    private final JobID currentJob;

    public OpenVillagerAdvancementsMenuMessage(
            BlockPos flagPos,
            UUID villagerUUID,
            Collection<JobID> unlockedJobs,
            JobID currentJob
    ) {
        this.flagPos = flagPos;
        this.villagerUUID = villagerUUID;
        this.unlockedJobs = unlockedJobs;
        this.currentJob = currentJob;
    }

    public static void encode(
            OpenVillagerAdvancementsMenuMessage msg,
            FriendlyByteBuf buffer
    ) {
        buffer.writeInt(msg.flagPos.getX());
        buffer.writeInt(msg.flagPos.getY());
        buffer.writeInt(msg.flagPos.getZ());
        buffer.writeUUID(msg.villagerUUID);
        buffer.writeCollection(msg.unlockedJobs, NetworkCompat::toNetwork);
        NetworkCompat.toNetwork(buffer, msg.currentJob());
    }

    public static OpenVillagerAdvancementsMenuMessage decode(FriendlyByteBuf buffer) {
        return new OpenVillagerAdvancementsMenuMessage(
                new BlockPos(buffer.readInt(), buffer.readInt(), buffer.readInt()),
                buffer.readUUID(),
                buffer.readList(NetworkCompat::fromNetworkJobID),
                NetworkCompat.fromNetworkJobID(buffer)
        );
    }

    public void handle(
            Supplier<NetworkEvent.Context> ctx
    ) {
        ToClientMessage.handle(
                ctx,
                () -> ClientAccess.openVillagerAdvancements(flagPos, villagerUUID, unlockedJobs, currentJob)
        );
    }

    public BlockPos flagPos() {
        return flagPos;
    }

    public UUID villagerUUID() {
        return villagerUUID;
    }

    public JobID currentJob() {
        return currentJob;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) return true;
        if (obj == null || obj.getClass() != this.getClass()) return false;
        var that = (OpenVillagerAdvancementsMenuMessage) obj;
        return Objects.equals(this.flagPos, that.flagPos) && Objects.equals(
                this.villagerUUID,
                that.villagerUUID
        ) && Objects.equals(this.unlockedJobs, that.unlockedJobs) && Objects.equals(this.currentJob, that.currentJob);
    }

    @Override
    public int hashCode() {
        return Objects.hash(flagPos, villagerUUID, unlockedJobs, currentJob);
    }

    @Override
    public String toString() {
        return "OpenVillagerAdvancementsMenuMessage[" + "flagPos=" + flagPos + ", " + "villagerUUID=" + villagerUUID + ", " + "unlockedJobs=" + unlockedJobs + ", " + "currentJob=" + currentJob + ']';
    }

}
