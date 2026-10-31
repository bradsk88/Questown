package ca.bradj.questown.gui.town.status;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

class ArrivalCardTest {

    @Test
    void anArrival_comesAfterTheVillagers() {
        Assertions.assertEquals(List.of("v1", "v2", "arrival"),
                ArrivalCard.order(List.of("v1", "v2"), "arrival", true, 3));
    }

    @Test
    void anArrival_staysOnPageOne_whenThePageIsFull() {
        Assertions.assertEquals(List.of("v1", "v2", "arrival", "v3", "v4"),
                ArrivalCard.order(List.of("v1", "v2", "v3", "v4"), "arrival", true, 3));
    }

    @Test
    void noArrival_justTheVillagers() {
        Assertions.assertEquals(List.of("v1", "v2"), ArrivalCard.order(List.of("v1", "v2"), "arrival", false, 3));
    }
}
