package ca.bradj.questown.core.network;

import ca.bradj.questown.QT;
import ca.bradj.questown.gui.villager.advancements.VillagerAdvancements;
import ca.bradj.questown.jobs.JobID;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

public record SyncVillagerAdvancementsMessage(
        Map<JobID, @Nullable JobID> parents,
        Map<JobID, ResourceLocation> icons
) {

    public static void encode(
            SyncVillagerAdvancementsMessage msg,
            FriendlyByteBuf buffer
    ) {
        buffer.writeInt(msg.parents.size());
        msg.parents.entrySet().forEach(i -> {
            buffer.writeUtf(i.getKey().id().toString());
            JobID value = i.getValue();
            if (value == null) {
                buffer.writeUtf("null");
            } else {
                buffer.writeUtf(value.id().toString());
            }
        });
        buffer.writeInt(msg.icons.size());
        msg.icons.entrySet().forEach(i -> {
            buffer.writeUtf(i.getKey().id().toString());
            buffer.writeResourceLocation(i.getValue());
        });
    }

    public static SyncVillagerAdvancementsMessage decode(FriendlyByteBuf buffer) {
        HashMap<JobID, JobID> b = new HashMap<>();
        int num = buffer.readInt();
        for (int i = 0; i < num; i++) {
            String job = buffer.readUtf();
            String parent = buffer.readUtf();
            JobID p = null;
            if (!parent.equals("null")) {
                p = JobID.fromRL(new ResourceLocation(parent));
            }
            b.put(JobID.fromRL(new ResourceLocation(job)), p);
        }
        HashMap<JobID, ResourceLocation> b2 = new HashMap<>();
         num = buffer.readInt();
        for (int i = 0; i < num; i++) {
            String job = buffer.readUtf();
            ResourceLocation icon = buffer.readResourceLocation();
            b2.put(JobID.fromRL(new ResourceLocation(job)), icon);
        }
        return new SyncVillagerAdvancementsMessage(b, b2);
    }

    public void handle(
            Supplier<NetworkEvent.Context> ctx
    ) {
        final AtomicBoolean success = new AtomicBoolean(false);
        ctx.get().enqueueWork(() -> {
            DistExecutor.unsafeRunWhenOn(
                    Dist.CLIENT,
                    () -> () -> {
                        parents.forEach(VillagerAdvancements::registerOnClientSide);
                        VillagerAdvancements.registerIcons(icons);
                        success.set(true);
                    }
            );
        }).exceptionally(SyncVillagerAdvancementsMessage::logError);
        ctx.get().setPacketHandled(true);

    }

    private static Void logError(Throwable ex) {
        QT.GUI_LOGGER.error("Failed to send wanted ingredients to player", ex);
        return null;
    }
}
