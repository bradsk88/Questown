package ca.bradj.questown.integration.minecraft;

import ca.bradj.questown.items.StockRequestItem;
import ca.bradj.questown.jobs.requests.WorkRequest;
import ca.bradj.roomrecipes.core.space.InclusiveSpace;
import ca.bradj.roomrecipes.core.space.Position;
import ca.bradj.roomrecipes.serialization.MCRoom;
import com.google.common.collect.ImmutableList;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * The town adds a stock request's room and workspot to its own copy of the item, not to the item in
 * the chest, so loading a saved town must not report that difference as items that may be lost.
 */
class TownStateSerializerItemCheckTest {

    private static final MCRoom STORE_ROOM = new MCRoom(
            new Position(-4, 2),
            ImmutableList.of(InclusiveSpace.from(-6, -2).to(-2, 2)),
            64
    );
    private static final BlockPos CHEST = new BlockPos(-4, 64, -1);

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    // The registered stock_request item isn't available in unit tests; paper carries the same NBT.
    private static MCTownItem request(WorkRequest wanted) {
        CompoundTag nbt = new CompoundTag();
        StockRequestItem.writeToNBT(nbt, wanted);
        return new MCTownItem(Items.PAPER, 1, nbt);
    }

    private static MCTownItem withWorkSpot(MCTownItem item) {
        MCTownItem copy = item.copy();
        copy.setNBT(tag -> StockRequestItem.writeToNBT(tag, STORE_ROOM, CHEST));
        return copy;
    }

    @Test
    void sameApartFromWorkSpot_shouldMatch_whenOnlyTheStateCopyHasAWorkSpot() {
        MCTownItem inChest = request(WorkRequest.of(Items.EMERALD));

        Assertions.assertTrue(
                TownStateSerializer.sameApartFromWorkSpot(inChest, withWorkSpot(inChest)),
                "a stock request differing only by its workspot was reported as a mismatch"
        );
    }

    @Test
    void sameApartFromWorkSpot_shouldNotMatch_whenTheRequestsDiffer() {
        MCTownItem inChest = request(WorkRequest.of(Items.EMERALD));
        MCTownItem inState = withWorkSpot(request(WorkRequest.of(Items.DIAMOND)));

        Assertions.assertFalse(
                TownStateSerializer.sameApartFromWorkSpot(inChest, inState),
                "stock requests for different items were reported as the same"
        );
    }

    @Test
    void sameApartFromWorkSpot_shouldNotMatch_whenTheItemsDiffer() {
        MCTownItem inChest = new MCTownItem(Items.EMERALD, 1, new CompoundTag());
        MCTownItem inState = new MCTownItem(Items.DIAMOND, 1, new CompoundTag());

        Assertions.assertFalse(
                TownStateSerializer.sameApartFromWorkSpot(inChest, inState),
                "different items were reported as the same"
        );
    }
}
