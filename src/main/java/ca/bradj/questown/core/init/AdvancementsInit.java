package ca.bradj.questown.core.init;

import ca.bradj.questown.core.advancements.ApproachTownTrigger;
import ca.bradj.questown.core.advancements.VisitorTrigger;
import net.minecraft.advancements.CriteriaTriggers;

public class AdvancementsInit {

    public static ApproachTownTrigger APPROACH_TOWN_TRIGGER;
    public static VisitorTrigger VISITOR_TRIGGER;

    public static void register() {
        VISITOR_TRIGGER = CriteriaTriggers.register(new VisitorTrigger());
        APPROACH_TOWN_TRIGGER = CriteriaTriggers.register(new ApproachTownTrigger());
    }
}
