package ca.bradj.questown.core.init;

import ca.bradj.questown.Questown;
import ca.bradj.questown.core.advancements.ApproachTownTrigger;
import ca.bradj.questown.core.advancements.RoomTrigger;
import ca.bradj.questown.core.advancements.TutorialTrigger;
import ca.bradj.questown.core.advancements.VisitorTrigger;
import net.minecraft.advancements.CriterionTrigger;
import net.minecraft.core.registries.Registries;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.RegisterEvent;

/**
 * Custom advancement criterion triggers. 1.20.6's trigger-type registry is baked at bootstrap
 * (the old {@code CriteriaTriggers.register} path throws "already frozen"), so the triggers go
 * through a mod-bus {@link DeferredRegister} on {@link Registries#TRIGGER_TYPE}.
 *
 * The convenience statics below are bound when the registry's RegisterEvent fires, so they are
 * only usable during gameplay (after load) — the same constraint the old path had.
 */
public class AdvancementsInit {

    private static final DeferredRegister<CriterionTrigger<?>> TRIGGERS =
            DeferredRegister.create(Registries.TRIGGER_TYPE, Questown.MODID);

    private static final DeferredHolder<CriterionTrigger<?>, VisitorTrigger> VISITOR_H =
            TRIGGERS.register("visitor_trigger", VisitorTrigger::new);
    private static final DeferredHolder<CriterionTrigger<?>, RoomTrigger> ROOM_H =
            TRIGGERS.register("room_trigger", RoomTrigger::new);
    private static final DeferredHolder<CriterionTrigger<?>, ApproachTownTrigger> APPROACH_H =
            TRIGGERS.register("approach_town", ApproachTownTrigger::new);
    private static final DeferredHolder<CriterionTrigger<?>, TutorialTrigger> TUTORIAL_H =
            TRIGGERS.register("tutorial_trigger", TutorialTrigger::new);

    public static VisitorTrigger VISITOR_TRIGGER;
    public static RoomTrigger ROOM_TRIGGER;
    public static ApproachTownTrigger APPROACH_TOWN_TRIGGER;
    public static TutorialTrigger TUTORIAL_TRIGGER;

    public static void init(IEventBus modBus) {
        TRIGGERS.register(modBus);
        modBus.addListener(RegisterEvent.class, event -> {
            if (event.getRegistry(Registries.TRIGGER_TYPE) != null) {
                VISITOR_TRIGGER = VISITOR_H.value();
                ROOM_TRIGGER = ROOM_H.value();
                APPROACH_TOWN_TRIGGER = APPROACH_H.value();
                TUTORIAL_TRIGGER = TUTORIAL_H.value();
            }
        });
    }
}
