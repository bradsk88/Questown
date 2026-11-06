package ca.bradj.questown.town.needs;

import ca.bradj.questown.town.TownPossibleWork;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** An unlit smelter with input counts as needing coal, not as a missing room. */
class SmelterFuelGateReasonTest {

    @Test
    void fuelKeyIsTheVanillaCoalsTag() {
        assertEquals("#minecraft:coals", TownPossibleWork.SMELTER_FUEL_KEY);
    }

    @Test
    void fuelBlockedClassifiesAsASupplyNeedOnTheFuel() {
        GateReason r = new GateReason.Blocked(0, List.of("#minecraft:coals"), List.of());
        assertTrue(r.actionable(), "the batch can build toward the fuel (someone must mine/bring it)");
        assertTrue(r.supplyItem(), "it routes to the item store - the economics tab + bubble key to the fuel");
        assertEquals("#minecraft:coals", r.itemKey(), "the tracker keys to the BLOCKING INPUT, never the product");
    }

}
