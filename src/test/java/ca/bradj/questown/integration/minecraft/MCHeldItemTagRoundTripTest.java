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

/** {@code MCTownItem.of} unwraps the "item" compound itself, so callers must pass the outer tag. Unwrapping twice turns every item into AIR. */
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
        MCTownItem back = MCTownItem.of(outerTag);
        assertFalse(back.isEmpty(), "outer tag must unwrap to the item, not AIR");
        assertEquals(1, back.quantity(), "quantity must survive the round-trip");
        assertEquals(Items.WOODEN_HOE, back.get(), "item identity must survive");
    }

    @Test
    void of_innerCompoundAfterPreUnwrap_collapsesToAir() {
        // The inner compound has no "item" compound, so of() gives AIR.
        MCTownItem item = MCTownItem.fromMCItemStack(new ItemStack(Items.WOODEN_HOE, 1));
        var outerTag = item.serializeNBT();
        MCTownItem preUnwrapped = MCTownItem.of(outerTag.getCompound("item"));
        assertTrue(preUnwrapped.isEmpty(),
                "of(inner) must be AIR; the round-trip fix belongs in fromTag/deserialize");
    }
}
