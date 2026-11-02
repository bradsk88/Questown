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

/** The flag link a welcome mat is crafted with survives the save and read. */
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
