package ca.bradj.questown.town.needs;

import ca.bradj.questown.town.TownPossibleWork;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The fuel-surfacing half of the 2026-10-13 ruling (b): a smelter job whose room check
 * fails while an UNLIT smelter block physically exists in the base room is named as a
 * supply need on {@code #minecraft:coals} - not as a room gap. The routing consequence
 * (item store + UNMET_ITEM bubble + economics tab all key to the fuel) rides on
 * {@link GateReason.Blocked#supplyItem()}, pinned here.
 */
class SmelterFuelGateReasonTest {

    @Test
    void fuelKeyIsTheVanillaCoalsTag() {
        // The bubble/econ string the surfacing registers. The client resolves it through
        // Ingredients.fromString -> Ingredient.of(ItemTags.COALS), so pinning the string
        // pins the whole naming path without the registry.
        assertEquals("#minecraft:coals", TownPossibleWork.SMELTER_FUEL_KEY);
    }

    @Test
    void fuelBlockedClassifiesAsASupplyNeedOnTheFuel() {
        // What TownPossibleWork.gateReason returns for a fuel-labelled JobFacts:
        GateReason r = new GateReason.Blocked(0, List.of("#minecraft:coals"), List.of());
        assertTrue(r.actionable(), "the batch can build toward the fuel (someone must mine/bring it)");
        assertTrue(r.supplyItem(), "it routes to the item store - the economics tab + bubble key to the fuel");
        assertEquals("#minecraft:coals", r.itemKey(), "the tracker keys to the BLOCKING INPUT, never the product");
    }

    /**
     * The live-detection half ({@code TownPossibleWork.smelterFuelBlocker}: room-handle scan +
     * the {@code AbstractFurnaceBlock.LIT} blockstate read) is registry-bound - it needs a
     * {@code TownFlagBlockEntity} + a live {@code ServerLevel}. Covered by the held
     * self-sufficiency run instead (a cook blocked on fuel shows the UNMET_ITEM bubble + the
     * econ tab naming coal).
     */
    @Disabled("registry-bound: needs a live TownFlagBlockEntity + ServerLevel")
    @Test
    void smelterFuelBlocker_detectionHeldRunCovered() {
    }
}
