package ca.bradj.questown.blocks;

import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Pins the parent-NBT contract the welcome-mat placement path relies on:
 * {@code WelcomeMatBlock.getStateForPlacement} drops an unstamped mat
 * ("Welcome mat is not associated to an existing flag") because
 * {@code TownFlagBlock.GetParentFromNBT} needs the
 * tags. The menu-craft path (FlagCraftMessage.craftWelcomeMat) stamps the
 * flag the player crafted at; this test guards the stamp/read round-trip it
 * depends on (the 2026-10-11 playtest: menu-crafted mat placed, both
 * attempts dropped).
 */
class WelcomeMatParentNbtTest {

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void storeParent_roundTripsThroughGetParentPos() {
        ItemStack mat = Items.OAK_PRESSURE_PLATE.getDefaultInstance();
        BlockPos flagPos = new BlockPos(-108, 64, 31);

        TownFlagBlock.StoreParentOnNBT(mat, flagPos);

        assertEquals(flagPos, TownFlagBlock.GetParentPosFromNBT(mat));
    }

    @Test
    void unstamped_itemHasNoParent() {
        ItemStack mat = Items.OAK_PRESSURE_PLATE.getDefaultInstance();

        assertNull(TownFlagBlock.GetParentPosFromNBT(mat));
    }
}
