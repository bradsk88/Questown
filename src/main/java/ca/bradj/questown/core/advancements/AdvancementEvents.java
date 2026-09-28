package ca.bradj.questown.core.advancements;

import ca.bradj.questown.Questown;
import ca.bradj.questown.mc.Compat;
import ca.bradj.questown.mc.Journal;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.entity.player.AdvancementEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;

@net.neoforged.fml.common.EventBusSubscriber(modid = Questown.MODID, bus = net.neoforged.fml.common.EventBusSubscriber.Bus.MOD)
public class AdvancementEvents {

    private static final ResourceLocation BOOK_ID = new ResourceLocation(Questown.MODID, "intro");

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
            TutorialTrigger.Triggers.Chapter4.getID(),
            TutorialTrigger.Triggers.FirstCampfireSleep.getID()
    );

    // Maps advancement path → journal entry path to open automatically when the advancement fires.
    // Entries without a mapping fall back to a chat notification only.
    private static final ImmutableMap<String, String> ADVANCEMENT_TO_ENTRY = ImmutableMap.<String, String>builder()
            .put(VisitorTrigger.Triggers.FirstVisitor.getID(),         "entries/002-how-to-start")
            .put(RoomTrigger.Triggers.WandGet.getID(),                 "entries/003-how-to-register")
            .put(RoomTrigger.Triggers.FirstRoom.getID(),               "entries/004-job-board")
            .put(RoomTrigger.Triggers.FirstJobBoard.getID(),           "entries/005-requests")
            .put(VisitorTrigger.Triggers.FirstJobRequest.getID(),      "entries/006-town-gate")
            .put(RoomTrigger.Triggers.FirstWelcomeMat.getID(),         "entries/007-storage")
            .put(RoomTrigger.Triggers.FirstStoreRoom.getID(),          "entries/008-how-to-supply")
            .put(VisitorTrigger.Triggers.FirstLeaveToGather.getID(),   "entries/009-how-to-monitor")
            .put(RoomTrigger.Triggers.FirstOpenFlagMenu.getID(),       "entries/010-how-to-progress")
            .put(VisitorTrigger.Triggers.FirstJobDone.getID(),         "entries/011-how-to-level")
            // FirstJobQuest fires right after FirstJobDone; omitting it here so 011 isn't immediately overridden
            .put(VisitorTrigger.Triggers.FirstUnmetNeeds.getID(),      "entries/lesson_needs")
            .put(RoomTrigger.Triggers.FirstJobBlock.getID(),           "entries/lesson_room_recipes")
            .put(TutorialTrigger.Triggers.TutorialComplete.getID(),    "entries/013-the-crafter")
            .put(TutorialTrigger.Triggers.SecondJobType.getID(),       "entries/014-supply-chains")
            .put(TutorialTrigger.Triggers.FirstWarp.getID(),           "entries/015-while-you-were-away")
            .put(TutorialTrigger.Triggers.FirstRoomUpgrade.getID(),    "entries/lesson_upgrades")
            .put(TutorialTrigger.Triggers.FirstBopView.getID(),        "entries/lesson_bop")
            .put(TutorialTrigger.Triggers.FirstCampfireSleep.getID(),  "entries/lesson_campfire_sleep")
            .build();

    @SubscribeEvent
    public static void entityAttrEvent(AdvancementEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer sp)) {
            return;
        }
        if (!Questown.MODID.equals(event.getAdvancement().id().getNamespace())) {
            return;
        }
        String path = event.getAdvancement().id().getPath();
        if ("root".equals(path)) {
            // R20/R21: journal is no longer handed out on first visit — the helper
            // chicken is the onboarding guide now. The chat line stays as flavour,
            // and Patchouli entries still unlock through openBookEntry below.
            Compat.sendMessage(sp, Compat.translatable("messages.town_flag.first_visit_journal"));
            return;
        }
        if (!ADVANCEMENTS_WITH_PAGES.contains(path)) {
            return;
        }
        String entry = ADVANCEMENT_TO_ENTRY.get(path);
        if (entry != null) {
            Journal.openBookEntry(sp, BOOK_ID, new ResourceLocation(Questown.MODID, entry), 0);
        } else {
            Compat.sendMessage(sp, Compat.translatable("messages.town_flag.journal_page"));
        }
    }

}
