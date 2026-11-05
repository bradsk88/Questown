package ca.bradj.questown.loader.forge;

import ca.bradj.questown.core.advancements.ApproachTownTrigger;
import ca.bradj.questown.core.advancements.RoomTrigger;
import ca.bradj.questown.core.advancements.TutorialTrigger;
import ca.bradj.questown.core.advancements.VisitorTrigger;
import ca.bradj.questown.core.init.AdvancementsInit;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraftforge.eventbus.api.IEventBus;

/** 1.19.2 registers criterion triggers statically (1.20.6 bakes them into a registry). */
public final class ForgeAdvancementTriggers {

    private ForgeAdvancementTriggers() {
    }

    public static void init(IEventBus modBus) {
        AdvancementsInit.VISITOR_TRIGGER = CriteriaTriggers.register(new VisitorTrigger());
        AdvancementsInit.ROOM_TRIGGER = CriteriaTriggers.register(new RoomTrigger());
        AdvancementsInit.APPROACH_TOWN_TRIGGER = CriteriaTriggers.register(new ApproachTownTrigger());
        AdvancementsInit.TUTORIAL_TRIGGER = CriteriaTriggers.register(new TutorialTrigger());
    }
}
