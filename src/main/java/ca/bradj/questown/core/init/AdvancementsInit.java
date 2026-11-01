package ca.bradj.questown.core.init;

import ca.bradj.questown.core.advancements.ApproachTownTrigger;
import ca.bradj.questown.core.advancements.RoomTrigger;
import ca.bradj.questown.core.advancements.TutorialTrigger;
import ca.bradj.questown.core.advancements.VisitorTrigger;

/**
 * Static handles to the four custom advancement criterion triggers, bound once at load
 * by the loader's trigger-type registration (see {@code loader.neo.NeoAdvancementTriggers}).
 *
 * <p>The bind is deferred to registry load time (1.20.6's trigger-type registry is baked at
 * bootstrap), so these statics are only usable during gameplay (after load) - the same
 * constraint the old path had.
 */
public class AdvancementsInit {

    public static VisitorTrigger VISITOR_TRIGGER;
    public static RoomTrigger ROOM_TRIGGER;
    public static ApproachTownTrigger APPROACH_TOWN_TRIGGER;
    public static TutorialTrigger TUTORIAL_TRIGGER;
}
