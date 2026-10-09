package ca.bradj.questown.town.needs;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;


/**
 * Pins the typed unmet-need gate reasons (design:
 * {@code .scratch/gannet-top-need/unmet-need-design.md}): no room built beats
 * a missing input, which beats "product not requested". Precedence and key
 * choice are pure decisions over labels and booleans; the town side supplies
 * the labels from the registry-bound job data and container scans (covered by
 * compile + the held self_sufficiency run).
 */
class GateReasonTest {

    private static final List<String> NONE = List.of();

    @Test
    void noRoomBuiltWinsOverEverything() {
        GateReason r = GateReason.classify(false, "block.questown.mineshaft", -1, NONE, NONE, true, "coal");
        assertTrue(r instanceof GateReason.NoRoom);
        assertEquals("block.questown.mineshaft", ((GateReason.NoRoom) r).room());
        assertEquals("coal", r.itemKey());
        assertTrue(r.actionable());
    }

    @Test
    void noRoomAppliedEvenWhenNothingIsRequested() {
        GateReason r = GateReason.classify(false, "room", -1, NONE, NONE, false, "coal");
        assertTrue(r instanceof GateReason.NoRoom);
        assertEquals("room", ((GateReason.NoRoom) r).room());
        assertTrue(r.actionable());
    }

    @Test
    void noRoomWithoutANameableSupplyStillNamesTheRoom() {
        GateReason r = GateReason.classify(false, "room", -1, NONE, NONE, true, null);
        assertTrue(r instanceof GateReason.NoRoom);
        assertEquals("room", ((GateReason.NoRoom) r).room());
        assertNull(r.itemKey());
    }

    @Test
    void blockedKeyIsFirstMissingIngredient() {
        GateReason r = GateReason.classify(true, "room", 2, List.of("#fuel", "#charcoal"), NONE, true, "coal");
        assertTrue(r instanceof GateReason.Blocked);
        GateReason.Blocked b = (GateReason.Blocked) r;
        assertEquals(2, b.state());
        assertEquals(List.of("#fuel", "#charcoal"), b.missingIngredients());
        assertEquals("#fuel", r.itemKey());
        assertTrue(r.actionable());
    }

    @Test
    void blockedKeyFallsBackToFirstMissingTool() {
        GateReason r = GateReason.classify(true, "room", 0, NONE, List.of("#questown:swords"), false, "meat");
        assertTrue(r instanceof GateReason.Blocked);
        GateReason.Blocked b = (GateReason.Blocked) r;
        assertEquals(0, b.state());
        assertEquals(List.of("#questown:swords"), b.missingTools());
        assertEquals("#questown:swords", r.itemKey());
        assertTrue(r.actionable());
    }

    @Test
    void blockedWithNoNameableMissingItemIsNotActionable() {
        GateReason r = GateReason.classify(true, "room", 1, NONE, NONE, true, "x");
        assertTrue(r instanceof GateReason.Blocked);
        assertNull(r.itemKey());
        assertFalse(r.actionable());
    }

    @Test
    void noRequestWhenInputsFineButProductNotOnBoard() {
        GateReason r = GateReason.classify(true, "room", -1, NONE, NONE, false, "bread");
        assertTrue(r instanceof GateReason.NoRequest);
        assertFalse(r.actionable());
        assertNull(r.itemKey());
    }

    @Test
    void okWhenEverythingIsFine() {
        GateReason r = GateReason.classify(true, "room", -1, NONE, NONE, true, "bread");
        assertTrue(r instanceof GateReason.Ok);
        assertFalse(r.actionable());
        assertNull(r.itemKey());
    }

    // A supply-item need is one the town writes to its ITEM store (and shows as
    // a UNMET_ITEM bubble). NoRoom is a job-site gap (room store / work-seeker status,
    // not the item store) and NoRequest is a board problem; only a Blocked on a
    // missing ingredient/tool names a supply item. itemKey() may still carry the
    // job's product (the tracker's producer handle) without it being a supply need.
    @Test
    void noRoomIsNotASupplyItem_evenThoughItCarriesTheProductKey() {
        GateReason r = GateReason.classify(false, "block.questown.mineshaft", -1, NONE, NONE, true, "coal");
        assertTrue(r instanceof GateReason.NoRoom);
        assertEquals("coal", r.itemKey());
        assertFalse(r.supplyItem());
    }

    @Test
    void blockedOnAMissingIngredientIsASupplyItem() {
        GateReason r = GateReason.classify(true, "room", 2, List.of("#fuel", "#charcoal"), NONE, true, "coal");
        assertTrue(r.supplyItem());
        assertEquals("#fuel", r.itemKey());
    }

    @Test
    void blockedOnAMissingToolIsASupplyItem() {
        GateReason r = GateReason.classify(true, "room", 0, NONE, List.of("#questown:swords"), false, "meat");
        assertTrue(r.supplyItem());
        assertEquals("#questown:swords", r.itemKey());
    }

    @Test
    void blockedWithNoNameableInputIsNotASupplyItem() {
        GateReason r = GateReason.classify(true, "room", 1, NONE, NONE, true, "x");
        assertTrue(r instanceof GateReason.Blocked);
        assertFalse(r.supplyItem());
    }

    @Test
    void noRequestAndOkAreNotSupplyItems() {
        assertFalse(GateReason.classify(true, "room", -1, NONE, NONE, false, "bread").supplyItem());
        assertFalse(GateReason.classify(true, "room", -1, NONE, NONE, true, "bread").supplyItem());
    }
}
