package ca.bradj.questown.core.advancements;

import ca.bradj.questown.Questown;
import com.google.common.collect.ImmutableList;
import net.minecraft.network.chat.TranslatableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.entity.player.AdvancementEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import vazkii.patchouli.api.PatchouliAPI;

@Mod.EventBusSubscriber(modid = Questown.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class AdvancementEvents {

    public static final ImmutableList<String> ADVANCEMENTS_WITH_PAGES = ImmutableList.of(
            VisitorTrigger.Triggers.FirstVisitor.getID(),
            VisitorTrigger.Triggers.FirstJobQuest.getID(),
            RoomTrigger.Triggers.FirstRoom.getID()
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
        if ("root".equals(path)){
            sp.sendMessage(new TranslatableComponent("messages.town_flag.first_visit_journal"), sp.getUUID());
            sp.addItem(PatchouliAPI.get().getBookStack(new ResourceLocation(Questown.MODID, "intro")));
            return;
        }
        if (ADVANCEMENTS_WITH_PAGES.contains(path)) {
            sp.sendMessage(new TranslatableComponent("messages.town_flag.journal_page"), sp.getUUID());
        }
    }

}
