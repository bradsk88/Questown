package ca.bradj.questown.core.advancements;

import com.google.gson.JsonPrimitive;
import net.minecraft.advancements.critereon.EntityPredicate;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TutorialTriggerTest {

    @Test
    void allTriggersExceptInvalidHaveStringIds() {
        for (TutorialTrigger.Triggers trigger : TutorialTrigger.Triggers.values()) {
            if (trigger == TutorialTrigger.Triggers.Invalid) {
                continue;
            }
            assertNotNull(trigger.getID(), "Trigger " + trigger + " should have a string ID");
            assertFalse(trigger.getID().isEmpty(), "Trigger " + trigger + " should have a non-empty string ID");
        }
    }

    @Test
    void invalidTriggerHasNoStringId() {
        assertNull(TutorialTrigger.Triggers.Invalid.getID());
    }

    @Test
    void fromJSON_roundTrips() {
        for (TutorialTrigger.Triggers trigger : TutorialTrigger.Triggers.values()) {
            if (trigger == TutorialTrigger.Triggers.Invalid) {
                continue;
            }
            String id = trigger.getID();
            TutorialTrigger.Triggers parsed = TutorialTrigger.Triggers.fromJSON(new JsonPrimitive(id));
            assertEquals(trigger, parsed, "Round-trip failed for trigger " + trigger);
        }
    }

    @Test
    void fromJSON_unknownIdThrows() {
        assertThrows(IllegalArgumentException.class, () ->
                TutorialTrigger.Triggers.fromJSON(new JsonPrimitive("bogus"))
        );
    }

    @Test
    void instanceRejectsInvalidContext() {
        assertThrows(IllegalArgumentException.class, () ->
                new TutorialTrigger.Instance(EntityPredicate.Composite.ANY, TutorialTrigger.Triggers.Invalid)
        );
    }

    @Test
    void instanceMatchesCorrectTrigger() {
        TutorialTrigger.Instance instance = new TutorialTrigger.Instance(
                EntityPredicate.Composite.ANY, TutorialTrigger.Triggers.TutorialComplete
        );
        assertTrue(instance.matches(TutorialTrigger.Triggers.TutorialComplete));
    }

    @Test
    void instanceDoesNotMatchDifferentTrigger() {
        TutorialTrigger.Instance instance = new TutorialTrigger.Instance(
                EntityPredicate.Composite.ANY, TutorialTrigger.Triggers.TutorialComplete
        );
        assertFalse(instance.matches(TutorialTrigger.Triggers.SecondJobType));
    }
}
