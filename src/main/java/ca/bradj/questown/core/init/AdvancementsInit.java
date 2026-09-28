package ca.bradj.questown.core.init;

import ca.bradj.questown.core.advancements.ApproachTownTrigger;
import ca.bradj.questown.core.advancements.RoomTrigger;
import ca.bradj.questown.core.advancements.TutorialTrigger;
import ca.bradj.questown.core.advancements.VisitorTrigger;
import net.minecraft.advancements.CriteriaTriggers;

public class AdvancementsInit {

    public static ApproachTownTrigger APPROACH_TOWN_TRIGGER;
    public static VisitorTrigger VISITOR_TRIGGER;
    public static RoomTrigger ROOM_TRIGGER;
    public static TutorialTrigger TUTORIAL_TRIGGER;

    public static void register() {
        VISITOR_TRIGGER = CriteriaTriggers.register(VisitorTrigger.ID.toString(), new VisitorTrigger());
        ROOM_TRIGGER = CriteriaTriggers.register(RoomTrigger.ID.toString(), new RoomTrigger());
        APPROACH_TOWN_TRIGGER = CriteriaTriggers.register(ApproachTownTrigger.ID.toString(), new ApproachTownTrigger());
        TUTORIAL_TRIGGER = CriteriaTriggers.register(TutorialTrigger.ID.toString(), new TutorialTrigger());
    }
}
