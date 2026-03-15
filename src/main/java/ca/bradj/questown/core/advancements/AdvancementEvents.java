package ca.bradj.questown.core.advancements;

import ca.bradj.questown.Questown;
import ca.bradj.questown.mc.Compat;
import com.google.common.collect.ImmutableList;
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
            VisitorTrigger.Triggers.FirstJobRequest.getID(),
            VisitorTrigger.Triggers.FirstLeaveToGather.getID(),
            VisitorTrigger.Triggers.FirstJobDone.getID(),
            VisitorTrigger.Triggers.FirstUnmetNeeds.getID(),
            RoomTrigger.Triggers.FirstRoom.getID(),
            RoomTrigger.Triggers.FirstJobBlock.getID(),
            RoomTrigger.Triggers.WandGet.getID(),
            RoomTrigger.Triggers.FirstJobBoard.getID(),
            RoomTrigger.Triggers.FirstStoreRoom.getID(),
            RoomTrigger.Triggers.FirstWelcomeMat.getID(),
            RoomTrigger.Triggers.FirstOpenFlagMenu.getID(),
            TutorialTrigger.Triggers.TutorialComplete.getID(),
            TutorialTrigger.Triggers.SecondJobType.getID(),
            TutorialTrigger.Triggers.FirstWarp.getID(),
            TutorialTrigger.Triggers.FirstRoomUpgrade.getID(),
            TutorialTrigger.Triggers.FirstBopView.getID(),
            TutorialTrigger.Triggers.FirstBopSpend.getID(),
            TutorialTrigger.Triggers.Chapter2.getID(),
            TutorialTrigger.Triggers.Chapter3.getID(),
            TutorialTrigger.Triggers.Chapter4.getID()
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
            Compat.sendMessage(sp, Compat.translatable("messages.town_flag.first_visit_journal"));
            sp.addItem(PatchouliAPI.get().getBookStack(new ResourceLocation(Questown.MODID, "intro")));
            return;
        }
        if (ADVANCEMENTS_WITH_PAGES.contains(path)) {
            Compat.sendMessage(sp, Compat.translatable("messages.town_flag.journal_page"));
        }
    }

}
