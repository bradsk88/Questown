package ca.bradj.questown.jobs.farmer;

import ca.bradj.questown.QT;
import ca.bradj.questown.core.Config;
import ca.bradj.questown.integration.minecraft.MCHeldItem;
import ca.bradj.questown.integration.minecraft.MCTownItem;
import ca.bradj.questown.jobs.FarmerJob;
import ca.bradj.questown.jobs.FarmerJournal;
import ca.bradj.questown.jobs.Jobs;
import ca.bradj.questown.jobs.WorkSpot;
import ca.bradj.questown.town.interfaces.TownInterface;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ComposterBlock;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.FarmBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.ToolActions;
import org.apache.logging.log4j.Marker;
import org.apache.logging.log4j.MarkerManager;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class WorldInteraction {

    private final Marker marker = MarkerManager.getMarker("WI").addParents(MarkerManager.getMarker("Farmer"));
    private final Container inventory;
    private final FarmerJournal<MCTownItem, MCHeldItem> journal;
    private int ticksSinceLastFarmAction;

    public WorldInteraction(Container inventory,
                            FarmerJournal<MCTownItem, MCHeldItem> journal
    ) {
        this.inventory = inventory;
        this.journal = journal;
    }

    public boolean tryFarming(
            TownInterface town,
            BlockPos entityPos,
            WorkSpot<BlockPos> workSpot
    ) {
        if (town.getServerLevel() == null) {
            return false;
        }

        ticksSinceLastFarmAction++;
        if (ticksSinceLastFarmAction < Config.FARM_ACTION_INTERVAL.get()) {
            return false;
        }
        ticksSinceLastFarmAction = 0;

        if (workSpot == null || workSpot.action == FarmerJob.FarmerAction.UNDEFINED) {
            return false;
        }

        BlockPos groundPos = workSpot.position;
        if (!Jobs.isCloseTo(entityPos, groundPos)) {
            return false;
        }
        BlockPos cropBlock = groundPos.above();

        return switch (workSpot.action) {
            case UNDEFINED -> false;
            case TILL -> tryTilling(town.getServerLevel(), groundPos);
            case PLANT -> tryPlanting(town.getServerLevel(), groundPos);
            case BONE -> tryBoning(town.getServerLevel(), cropBlock);
            case HARVEST -> tryHarvest(town.getServerLevel(), cropBlock);
            case COMPOST -> tryCompostSeeds(town.getServerLevel(), cropBlock);
        };
    }

    private boolean tryCompostSeeds(
            ServerLevel level,
            BlockPos cropBlock
    ) {
        BlockState oldState = level.getBlockState(cropBlock);
        if (oldState.getValue(ComposterBlock.LEVEL) == 8) {
            BlockState blockState = ComposterBlock.extractProduce(oldState, level, cropBlock);
            return !oldState.equals(blockState);
        }

        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack item = inventory.getItem(i);
            if (Items.WHEAT_SEEDS.equals(item.getItem())) {
                BlockState blockstate = ComposterBlock.insertItem(oldState, level, item, cropBlock);
                if (item.getCount() > 0) {
                    // didn't insert successfully
                    return false;
                }
                // Farmer gets a bonus "insert" that players don't get.
                item.grow(1);
                blockstate = ComposterBlock.insertItem(oldState, level, item, cropBlock);
                level.setBlockAndUpdate(cropBlock, blockstate);
                QT.JOB_LOGGER.debug(marker, "Farmer is removing {} from {}", item, inventory);
                inventory.setChanged();
                return true;
            }
        }

        return false;
    }

    private boolean tryBoning(
            ServerLevel level,
            BlockPos cropBlock
    ) {
        BlockState bs = level.getBlockState(cropBlock);
        if (bs.getBlock() instanceof CropBlock cb) {
            cb.performBonemeal(level, level.getRandom(), cropBlock, bs);
            BlockState after = level.getBlockState(cropBlock);
            if (!after.equals(bs)) {
                for (int i = 0; i < inventory.getContainerSize(); i++) {
                    ItemStack item = inventory.getItem(i);
                    if (Items.BONE_MEAL.equals(item.getItem())) {
                        QT.JOB_LOGGER.debug(marker, "Farmer is removing {} from {}", item, inventory);
                        inventory.removeItem(i, 1);
                        inventory.setChanged();
                        break;
                    }
                }
            }
        }
        return true;
    }

    private boolean tryHarvest(
            ServerLevel level,
            BlockPos cropBlock
    ) {
        BlockState bs = level.getBlockState(cropBlock);
        if (bs.getBlock() instanceof CropBlock cb) {
            if (cb.isMaxAge(bs)) {
                List<ItemStack> drops = CropBlock.getDrops(bs, level, cropBlock, null);
                drops.forEach(v -> {
                    if (journal.isInventoryFull()) {
                        level.addFreshEntity(new ItemEntity(
                                level,
                                cropBlock.getX(),
                                cropBlock.getY(),
                                cropBlock.getZ(),
                                v
                        ));
                    } else {
                        // TODO: Remember the location of the drop and come back to pick them up
                        journal.addItem(MCHeldItem.fromMCItemStack(v));
                    }
                });
                drops = CropBlock.getDrops(bs, level, cropBlock, null);
                drops.forEach(v -> {
                    if (journal.isInventoryFull()) {
                        level.addFreshEntity(new ItemEntity(
                                level,
                                cropBlock.getX(),
                                cropBlock.getY(),
                                cropBlock.getZ(),
                                v
                        ));
                    } else {
                        // TODO: Remember the location of the drop and come back to pick them up
                        journal.addItem(MCHeldItem.fromMCItemStack(v));
                    }
                });
                bs = bs.setValue(CropBlock.AGE, 0);
                level.setBlock(cropBlock, bs, 10);
            }
        }
        if (bs.is(Blocks.COMPOSTER)) {
            return tryCompostSeeds(level, cropBlock);
        }
        return true;
    }

    @Nullable
    private static boolean tryTilling(
            ServerLevel level,
            BlockPos groundPos
    ) {
        BlockState bs = getTilledState(level, groundPos);
        if (bs == null) return false;
        level.setBlock(groundPos, bs, 11);
        return true;
    }

    @Nullable
    public static BlockState getTilledState(
            ServerLevel level,
            BlockPos groundPos
    ) {
        BlockState bs = level.getBlockState(groundPos);
        BlockHitResult bhr = new BlockHitResult(
                Vec3.atCenterOf(groundPos), Direction.UP,
                groundPos, false
        );
        bs = bs.getToolModifiedState(new UseOnContext(
                level, null, InteractionHand.MAIN_HAND,
                // TODO: Determine tool from held item
                Items.WOODEN_HOE.getDefaultInstance(), bhr
        ), ToolActions.HOE_TILL, false);

        if (bs != null) {
            BlockState moistened = bs.setValue(FarmBlock.MOISTURE, 2);
            if (!moistened.equals(bs)) {
                return moistened;
            }
        }

        return null;
    }

    private boolean tryPlanting(
            ServerLevel level,
            BlockPos groundPos
    ) {
        BlockHitResult bhr = new BlockHitResult(
                Vec3.atCenterOf(groundPos), Direction.UP,
                groundPos, false
        );
        InteractionResult result = Items.WHEAT_SEEDS.useOn(new UseOnContext(
                level, null, InteractionHand.MAIN_HAND,
                Items.WHEAT_SEEDS.getDefaultInstance(), bhr
        ));
        if (result.consumesAction()) {
            for (int i = 0; i < inventory.getContainerSize(); i++) {
                ItemStack item = inventory.getItem(i);
                if (Items.WHEAT_SEEDS.equals(item.getItem())) {
                    QT.JOB_LOGGER.debug(marker, "Farmer is removing {} from {}", item, inventory);
                    inventory.removeItem(i, 1);
                    break;
                }
            }
        }
        return true;
    }
}
