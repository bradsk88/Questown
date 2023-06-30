package ca.bradj.questown.mobs.visitor;

import ca.bradj.questown.Questown;
import ca.bradj.questown.integration.minecraft.GathererStatuses;
import ca.bradj.questown.integration.minecraft.MCTownInventory;
import ca.bradj.questown.integration.minecraft.MCTownItem;
import ca.bradj.questown.jobs.GathererJournal;
import ca.bradj.questown.town.interfaces.TownInterface;
import com.google.common.collect.ImmutableList;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;

public class VisitorMobJob implements GathererJournal.SignalSource, GathererJournal.LootProvider<MCTownItem> {

    private GathererJournal.Signals signal;

    // TODO: Logic for changing jobs
    private final GathererJournal<MCTownInventory, MCTownItem> journal = new GathererJournal<>(
            this, () -> new MCTownItem(Items.AIR)
    ) {
        @Override
        protected void changeStatus(Statuses s) {
            super.changeStatus(s);
            Questown.LOGGER.debug("Changed status to {}", s);
        }
    };

    private final @Nullable ServerLevel level;
    FoodTarget foodTarget;

    public VisitorMobJob(@Nullable ServerLevel level) {
        this.level = level;
    }

    public void tick(
            Level level,
            BlockPos entityPos
    ) {
        processSignal(level, this);
        // TODO: Go to a chest and get food instead
//        simulateFoodAcquisition(level);
        simulateLootDeposit(level, entityPos);
    }

    private void simulateFoodAcquisition(Level level) {
        if (journal.getStatus() != GathererJournal.Statuses.NO_FOOD) {
            return;
        }
        if (level.getRandom().nextInt(100) == 0) {
            if (journal.inventoryIsFull()) {
                Questown.LOGGER.debug("Not adding food because inventory is full");
                return;
            }
            Questown.LOGGER.debug("Adding bread to inventory: {}", this.journal.getItems());
            this.journal.addItem(new MCTownItem(Items.BREAD));
        }
    }

    private void simulateLootDeposit(
            Level level,
            BlockPos entityPos
    ) {
        if (journal.getStatus() != GathererJournal.Statuses.RETURNED_SUCCESS) {
            return;
        }
        if (level.getRandom().nextInt(100) == 0) {
            Collection<MCTownItem> removed = journal.removeItems(v -> !v.isFood());
            removed.forEach(v -> level.addFreshEntity(new ItemEntity(
                    level, entityPos.getX(), entityPos.getY(), entityPos.getZ(), new ItemStack(v.get())
            )));
        }
    }

    private static void processSignal(
            Level level,
            VisitorMobJob e
    ) {
        if (level.isClientSide()) {
            return;
        }

        /*
         * Sunrise: 22000
         * Dawn: 0
         * Noon: 6000
         * Evening: 11500
         */

        long dayTime = level.getDayTime() % 24000;
        if (dayTime < 6000) {
            e.signal = GathererJournal.Signals.MORNING;
        } else if (dayTime < 11500) {
            e.signal = GathererJournal.Signals.NOON;
        } else if (dayTime < 22000) {
            e.signal = GathererJournal.Signals.EVENING;
        } else {
            e.signal = GathererJournal.Signals.NIGHT;
        }
        e.journal.tick(e);
    }

    public GathererJournal.Signals getSignal() {
        return this.signal;
    }

    public Collection<MCTownItem> getLoot() {
        if (level == null) {
            return ImmutableList.of();
        }
        LootTable lootTable = level.getServer().getLootTables().get(
                // TODO: Own loot table
                new ResourceLocation("minecraft", "chests/spawn_bonus_chest")
        );
        LootContext.Builder lcb = new LootContext.Builder((ServerLevel) level);
        LootContext lc = lcb.create(LootContextParamSets.EMPTY);
        // TODO: Maybe add this once the entity is not a BlockEntity?
//        LootContext lc = lcb
//                .withParameter(LootContextParams.THIS_ENTITY, this)
//                .withParameter(LootContextParams.ORIGIN, getBlockPos())
//                .create(LootContextParamSets.ADVANCEMENT_REWARD);

        // TODO: Do we need to grab random items from this list (for inter-mod support)?
        List<ItemStack> rItems = lootTable.getRandomItems(lc);
        int subLen = Math.min(rItems.size(), journal.getCapacity() - 1);
        List<MCTownItem> list = rItems.subList(0, subLen)
                .stream()
                .map(ItemStack::getItem)
                .map(MCTownItem::new)
                .toList();

        Questown.LOGGER.debug("[VMJ] Presenting items to gatherer: {}", list);

        return list;
    }

    public void initializeStatus(GathererStatuses status) {
        GathererJournal.Statuses value = status.toQT();
        Questown.LOGGER.debug("Initialized journal to state {}", value);
        this.journal.initializeStatus(value);
    }

    public BlockPos getTarget(TownInterface town) {
        BlockPos enterExitPos = getEnterExitPos(town); // TODO: Smarter logic? Town gate?
        switch (journal.getStatus()) {
            case NO_FOOD -> {
                Questown.LOGGER.debug("Visitor is searching for food");
                if (this.foodTarget != null) {
                    if (!this.foodTarget.hasItem(MCTownItem::isFood)) {
                        this.foodTarget = town.findMatchingContainer(MCTownItem::isFood);
                    }
                } else {
                    this.foodTarget = town.findMatchingContainer(MCTownItem::isFood);
                }
                if (this.foodTarget != null) {
                    Questown.LOGGER.debug("Located food at {}", this.foodTarget.getPosition());
                    return this.foodTarget.getPosition();
                } else {
                    Questown.LOGGER.debug("No food exists in town");
                    return town.getRandomWanderTarget();
                }
            }
            case UNSET, IDLE, NO_SPACE, STAYING -> {
                return null;
            }
            case GATHERING, RETURNING, CAPTURED -> {
                return enterExitPos;
            }
            case RETURNED_SUCCESS, RETURNED_FAILURE -> {
                return new BlockPos(town.getVisitorJoinPos());
            }
        }
        return null;
    }

    @NotNull
    private static BlockPos getEnterExitPos(TownInterface town) {
        return town.getTownFlagBasePos().offset(10, 0, 0);
    }

    public boolean shouldDisappear(
            TownInterface town,
            BlockPos entityPos
    ) {
        if (
                journal.getStatus() == GathererJournal.Statuses.GATHERING ||
                        journal.getStatus() == GathererJournal.Statuses.RETURNING ||
                        journal.getStatus() == GathererJournal.Statuses.CAPTURED
        ) {
            return isCloseTo(entityPos, getEnterExitPos(town));
        }
        return false;
    }

    private boolean isCloseTo(
            @NotNull BlockPos entityPos,
            @NotNull BlockPos targetPos
    ) {
        double d = targetPos.distToCenterSqr(
                entityPos.getX(), entityPos.getY(), entityPos.getZ()
        );
        return d < 5;
    }

    public boolean isCloseToFood(
            @NotNull BlockPos entityPos
    ) {
        if (foodTarget == null) {
            return false;
        }
        if (!foodTarget.hasItem(MCTownItem::isFood)) {
            return false;
        }
        return isCloseTo(entityPos, foodTarget.getPosition());
    }

    public void tryTakeFood(BlockPos entityPos) {
        if (journal.hasAnyFood()) {
            return;
        }
        if (!isCloseToFood(entityPos)) {
            return;
        }
        for (int i = 0; i < foodTarget.container.getContainerSize(); i++) {
            ItemStack iItem = foodTarget.container.getItem(i);
            MCTownItem mcTownItem = new MCTownItem(iItem.getItem());
            if (mcTownItem.isFood()) {
                Questown.LOGGER.debug("Gatherer is taking {} from {}", mcTownItem, foodTarget);
                journal.addItem(mcTownItem);
                foodTarget.container.removeItem(i, 1);
                break;
            }
        }
    }
}
