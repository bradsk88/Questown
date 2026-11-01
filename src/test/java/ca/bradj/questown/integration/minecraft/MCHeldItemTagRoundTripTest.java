package ca.bradj.questown.integration.minecraft;

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins the MCHeldItem NBT round-trip (TownStateSerializer's journal_items path).
 * <p>
 * {@code MCTownItem.of} unwraps the "item" compound itself, so {@code MCHeldItem.fromTag} /
 * {@code deserialize} must hand it the outer tag: a second unwrap sees an empty compound and every
 * item collapses to AIR (a town state read back from the flag tile with every villager's journal
 * emptied, so warp would score every townie as empty-handed).
 * <p>
 * The full serializeNBT->fromTag round-trip is registry-bound and pinned by the held_tool_warp
 * autotest scenario instead; the pure seam pinned here is the MCTownItem-of-compound contract: the
 * outer tag must unwrap to the item exactly once, and a pre-unwrapped (inner) compound must NOT.
 */
class MCHeldItemTagRoundTripTest {

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void of_outerTag_unwrapsOnceToTheItem() {
        MCTownItem item = MCTownItem.fromMCItemStack(new ItemStack(Items.WOODEN_HOE, 1));
        var outerTag = item.serializeNBT();
        // MCHeldItem.fromTag (fixed) hands this outer tag to of():
        MCTownItem back = MCTownItem.of(outerTag);
        assertFalse(back.isEmpty(), "outer tag must unwrap to the item, not AIR");
        assertEquals(1, back.quantity(), "quantity must survive the round-trip");
        assertEquals(Items.WOODEN_HOE, back.get(), "item identity must survive");
    }

    @Test
    void of_innerCompoundAfterPreUnwrap_collapsesToAir() {
        // of() on the ALREADY-unwrapped inner compound: it has no nested "item" compound, so of()
        // must return AIR. The unwrap belongs in fromTag/deserialize (passing the outer tag), not
        // in of() tolerating the inner shape.
        MCTownItem item = MCTownItem.fromMCItemStack(new ItemStack(Items.WOODEN_HOE, 1));
        var outerTag = item.serializeNBT();
        MCTownItem preUnwrapped = MCTownItem.of(outerTag.getCompound("item"));
        assertTrue(preUnwrapped.isEmpty(),
                "of(inner) must be AIR; the round-trip fix belongs in fromTag/deserialize");
    }
}
