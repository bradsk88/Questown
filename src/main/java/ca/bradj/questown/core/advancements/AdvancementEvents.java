package ca.bradj.questown.core.advancements;

import ca.bradj.questown.Questown;
import ca.bradj.questown.core.network.QuestownNetwork;
import ca.bradj.questown.core.network.SyncVillagerAdvancementsMessage;
import ca.bradj.questown.jobs.JobID;
import ca.bradj.questown.jobs.Work;
import ca.bradj.questown.jobs.Works;
import com.google.common.collect.ImmutableList;
import net.minecraft.network.chat.TranslatableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.entity.player.AdvancementEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;
import vazkii.patchouli.api.PatchouliAPI;

import java.util.HashMap;
import java.util.Map;

@Mod.EventBusSubscriber(modid = Questown.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class AdvancementEvents {

    public static final ImmutableList<String> ADVANCEMENTS_WITH_PAGES = ImmutableList.of(
            VisitorTrigger.Triggers.FirstVisitor.getID(),
            VisitorTrigger.Triggers.FirstJobQuest.getID(),
            VisitorTrigger.Triggers.FirstJobRequest.getID(),
            RoomTrigger.Triggers.FirstRoom.getID(),
            RoomTrigger.Triggers.FirstJobBlock.getID(),
            RoomTrigger.Triggers.WandGet.getID(),
            RoomTrigger.Triggers.FirstJobBoard.getID()
    );


    @SubscribeEvent
    public static void entityAttrEvent(AdvancementEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer sp)) {
            return;
        }
        if (!Questown.MODID.equals(event.getAdvancement().getId().getNamespace())) {
            return;
        }
        String path = event.getAdvancement().getId().getPath();
        if ("root".equals(path)) {
            sp.sendMessage(new TranslatableComponent("messages.town_flag.first_visit_journal"), sp.getUUID());
            sp.addItem(PatchouliAPI.get().getBookStack(new ResourceLocation(Questown.MODID, "intro")));
            return;
        }
        if (ADVANCEMENTS_WITH_PAGES.contains(path)) {
            sp.sendMessage(new TranslatableComponent("messages.town_flag.journal_page"), sp.getUUID());
        }
    }

    @SubscribeEvent()
    public static void sendVillagerAdvancementsToPlayer(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getPlayer() instanceof ServerPlayer sp)) {
            return;
        }
        PacketDistributor.PacketTarget tgt = PacketDistributor.PLAYER.with(() -> sp);
        Map<JobID, @Nullable JobID> b = new HashMap<>();
        Works.values().forEach(w -> {
            Work work = w.get();
            b.put(work.id(), work.parentID());
        });
        QuestownNetwork.CHANNEL.send(tgt, new SyncVillagerAdvancementsMessage(b));
    }

}
