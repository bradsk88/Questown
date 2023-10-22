package ca.bradj.questown.jobs;

import ca.bradj.questown.QT;
import ca.bradj.questown.core.init.TagsInit;
import ca.bradj.questown.integration.minecraft.MCHeldItem;
import ca.bradj.questown.integration.minecraft.MCTownItem;
import ca.bradj.questown.mobs.visitor.VisitorMobEntity;
import ca.bradj.questown.town.interfaces.TownInterface;
import com.google.common.collect.ImmutableList;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.apache.logging.log4j.Marker;
import org.apache.logging.log4j.MarkerManager;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.UUID;

public class SmelterJob extends ProductionJob<SmelterStatus, SimpleSnapshot<MCHeldItem>> {

    private static final ImmutableList<MCTownItem> allowedToPickUp = ImmutableList.of(
            MCTownItem.fromMCItemStack(Items.COAL.getDefaultInstance()),
            MCTownItem.fromMCItemStack(Items.IRON_ORE.getDefaultInstance()),
            MCTownItem.fromMCItemStack(Items.RAW_IRON.getDefaultInstance())
    );

    private static final ImmutableList<JobsClean.TestFn<MCTownItem>> recipe = ImmutableList.of(
            item -> Ingredient.of(TagsInit.Items.PICKAXES).test(item.toItemStack())
    );

    private static final Marker marker = MarkerManager.getMarker("Smelter");
    private Signals signal;

    public SmelterJob(
            UUID ownerUUID,
            int inventoryCapacity
    ) {
        super(ownerUUID, inventoryCapacity, allowedToPickUp, recipe, marker);
    }

    @Override
    protected Journal<SmelterStatus, MCHeldItem> initializeJournal(int inventoryCapacity) {
        return new SmelterJournal<>(
                () -> this.signal,
                inventoryCapacity,
                MCHeldItem::Air
        );
    }

    @Override
    public void tick(
            TownInterface town,
            BlockPos entityBlockPos,
            Vec3 entityPos,
            Direction facingPos
    ) {
        this.signal = Signals.fromGameTime(town.getServerLevel().getDayTime());
    }

    @Override
    public SimpleSnapshot<MCHeldItem> getJournalSnapshot() {
        return null;
    }

    @Override
    public void initialize(SimpleSnapshot<MCHeldItem> journal) {

    }

    @Override
    public void initializeStatusFromEntityData(@Nullable String s) {

    }

    @Override
    public String getStatusToSyncToClient() {
        return null;
    }

    @Override
    public boolean isJumpingAllowed(BlockState onBlock) {
        return true;
    }

    @Override
    protected Map<SmelterStatus, Boolean> getSupplyItemStatus() {
        return null;
    }

    @Override
    protected BlockPos getNonWorkTarget(
            BlockPos entityBlockPos,
            Vec3 entityPos,
            TownInterface town
    ) {
        return null;
    }

    @Override
    protected BlockPos goToProductionSpot() {
        return null;
    }

    @Override
    protected BlockPos goToJobSite() {
        return null;
    }

    @Override
    public int getStatusOrdinal() {
        return getStatus().ordinal();
    }

    @Override
    public boolean openScreen(
            ServerPlayer sp,
            VisitorMobEntity visitorMobEntity
    ) {
        // FIXME: Implement screen
        QT.JOB_LOGGER.error("No screen has been implemented for smelters");
        return false;
    }
}
