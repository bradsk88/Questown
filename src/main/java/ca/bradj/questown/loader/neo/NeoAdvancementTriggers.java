package ca.bradj.questown.loader.neo;

import ca.bradj.questown.Questown;
import ca.bradj.questown.core.advancements.ApproachTownTrigger;
import ca.bradj.questown.core.advancements.RoomTrigger;
import ca.bradj.questown.core.advancements.TutorialTrigger;
import ca.bradj.questown.core.advancements.VisitorTrigger;
import ca.bradj.questown.core.init.AdvancementsInit;
import net.minecraft.advancements.CriterionTrigger;
import net.minecraft.core.registries.Registries;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Trigger-type registration for the four leaf {@code CriterionTrigger}s. 1.20.6's trigger-type
 * registry is baked at bootstrap (the old {@code CriteriaTriggers.register} path throws
 * "already frozen"), so the deferred registration + the bind of the convenience statics in
 * {@link AdvancementsInit} happens in this mod-bus binder. The leaves keep only the statics and
 * the trigger implementations.
 *
 * <p>Called from the {@code @Mod} entry with the mod bus (where it used to live in
 * {@code core.init.AdvancementsInit.init(IEventBus)}).
 */
public class NeoAdvancementTriggers {

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

    public static void init(IEventBus modBus) {
        TRIGGERS.register(modBus);
        modBus.addListener(
            net.neoforged.neoforge.registries.RegisterEvent.class,
            event -> {
                if (event.getRegistry(Registries.TRIGGER_TYPE) != null) {
                    AdvancementsInit.VISITOR_TRIGGER = VISITOR_H.value();
                    AdvancementsInit.ROOM_TRIGGER = ROOM_H.value();
                    AdvancementsInit.APPROACH_TOWN_TRIGGER = APPROACH_H.value();
                    AdvancementsInit.TUTORIAL_TRIGGER = TUTORIAL_H.value();
                }
            }
        );
    }
}
