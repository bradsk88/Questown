package ca.bradj.questown.town.interfaces;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.UUID;

/** Chat names a villager by its "Villager N" name, or a shortened UUID. */
class VillagerHolderLabelTest {

    @Test
    void label_isTheDisplayName() {
        Assertions.assertEquals("Villager 3", VillagerHolder.label("Villager 3", UUID.randomUUID()));
    }

    @Test
    void label_fallsBackToAShortenedUuidString() {
        UUID u = UUID.randomUUID();
        String label = VillagerHolder.label("", u);
        Assertions.assertFalse(label.isEmpty());
        Assertions.assertNotEquals(u.toString(), label);
    }
}
