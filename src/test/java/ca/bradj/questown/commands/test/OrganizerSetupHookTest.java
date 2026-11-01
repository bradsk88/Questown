package ca.bradj.questown.commands.test;

import ca.bradj.questown.items.StockRequestItem;
import ca.bradj.questown.jobs.requests.WorkRequest;
import net.minecraft.SharedConstants;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Unit coverage for the seam the organizer setup hook depends on: fabricating a
 * {@link StockRequestItem} whose {@code request} NBT names the distinctive ingredient, exactly as
 * {@code CreateStockRequestFromUIMessage} does in-game. The workspot/job-block NBT is stamped at
 * runtime by {@code TownContainers.setWorkSpot} (based on the chest the clipboard sits in), so it
 * is not the hook's responsibility; the organizer autotest covers it, and the hook's chest
 * placement, end to end.
 */
class OrganizerSetupHookTest {

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void requestNbt_roundTripsToDistinctiveIngredient() {
        CompoundTag tag = new CompoundTag();
        WorkRequest request = WorkRequest.of(Items.DIAMOND);

        StockRequestItem.writeToNBT(tag, request);

        Assertions.assertTrue(StockRequestItem.hasRequest(tag));
        WorkRequest read = StockRequestItem.getRequest(tag);
        Assertions.assertEquals(request, read);
        Assertions.assertTrue(
                read.asIngredient().test(new ItemStack(Items.DIAMOND)),
                "round-tripped request must match the distinctive ingredient"
        );
        Assertions.assertFalse(
                read.asIngredient().test(new ItemStack(Items.STICK)),
                "round-tripped request must not match an unrelated item"
        );
    }

    @Test
    void freshTagHasNoRequest() {
        Assertions.assertFalse(StockRequestItem.hasRequest(new CompoundTag()));
        Assertions.assertFalse(StockRequestItem.hasRequest(null));
    }
}
