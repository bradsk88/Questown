package ca.bradj.questown.jobs.smelter;

import ca.bradj.questown.QT;
import ca.bradj.questown.blocks.SmeltingOvenBlock;
import ca.bradj.questown.core.Config;
import ca.bradj.questown.integration.minecraft.MCHeldItem;
import ca.bradj.questown.integration.minecraft.MCTownItem;
import ca.bradj.questown.jobs.Jobs;
import ca.bradj.questown.jobs.WorkSpot;
import ca.bradj.questown.town.interfaces.TownInterface;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;
import org.apache.logging.log4j.Marker;
import org.apache.logging.log4j.MarkerManager;
import org.jetbrains.annotations.Nullable;

public class WorldInteraction {
    private final Marker marker = MarkerManager.getMarker("WI").addParents(MarkerManager.getMarker("Smelter"));

    private final Container inventory;
    private final SmelterJournal<MCTownItem, MCHeldItem> journal;
    private int ticksSinceLastAction;

    public WorldInteraction(
            Container inventory,
            SmelterJournal<MCTownItem, MCHeldItem> journal
    ) {
        this.inventory = inventory;
        this.journal = journal;
    }

    public boolean tryWorking(
            TownInterface town,
            BlockPos entityPos,
            WorkSpot<SmelterAction, BlockPos> workSpot
    ) {
        if (town.getServerLevel() == null) {
            return false;
        }
        ServerLevel sl = town.getServerLevel();

        ticksSinceLastAction++;
        if (ticksSinceLastAction < Config.FARM_ACTION_INTERVAL.get()) { // TODO: Smelter specific config
            return false;
        }
        ticksSinceLastAction = 0;

        if (workSpot == null) {
            return false;
        }

        if (!Jobs.isCloseTo(entityPos, workSpot.position)) {
            return false;
        }


        return switch (workSpot.action) {
            case COLLECT_RAW_PRODUCT -> tryExtractOre(sl, workSpot.position);
            case PROCESSS_ORE -> tryProcessOre(sl, workSpot.position);
            case INSERT_ORE -> tryInsertIngredients(sl, workSpot.position);
            case UNDEFINED -> false;
        };
    }

    private boolean tryExtractOre(
            ServerLevel sl,
            BlockPos oldPos
    ) {
        if (SmeltingOvenBlock.hasOreToCollect(sl, oldPos)) {
            @Nullable BlockState newState = SmeltingOvenBlock.extractRawProduct(
                    sl, oldPos,
                    is -> journal.addItemIfSlotAvailable(MCHeldItem.fromMCItemStack(is))
            );
            return newState != null;
        }
        return false;
    }

    private boolean tryProcessOre(
            ServerLevel sl,
            BlockPos bp
    ) {
        if (SmeltingOvenBlock.canAcceptWork(sl, bp)) {
            BlockState blockState = SmeltingOvenBlock.applyWork(sl, bp);
            return blockState != null;
        }
        return false;
    }

    private boolean tryInsertIngredients(ServerLevel sl, BlockPos bp) {
        if (!SmeltingOvenBlock.canAcceptOre(sl, bp)) {
            return false;
        }

        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack item = inventory.getItem(i);
            String invBefore = inventory.toString();
            String name = "[unknown]";
            ResourceLocation registryName = item.getItem().getRegistryName();
            if (registryName != null) {
                name = registryName.toString();
            }
            // TODO: Accept more ores
            if (SmeltingOvenBlock.canAcceptOre(sl, bp) && Items.IRON_ORE.equals(item.getItem())) {
                SmeltingOvenBlock.insertItem(sl, bp, item);
                if (item.getCount() > 0) {
                    // didn't insert successfully
                    return false;
                }
                QT.JOB_LOGGER.debug(marker, "Smelter removed {} from {}", name, invBefore);
                inventory.setChanged();
                return true;
            }
        }
        return false;
    }
}
