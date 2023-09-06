package ca.bradj.questown.jobs;

import ca.bradj.questown.Questown;
import ca.bradj.questown.core.Config;
import ca.bradj.questown.gui.InventoryAndStatusMenu;
import ca.bradj.questown.integration.minecraft.MCHeldItem;
import ca.bradj.questown.integration.minecraft.MCTownItem;
import ca.bradj.questown.mobs.visitor.VisitorMobEntity;
import ca.bradj.questown.town.interfaces.TownInterface;
import ca.bradj.roomrecipes.adapter.Positions;
import ca.bradj.roomrecipes.core.space.InclusiveSpace;
import ca.bradj.roomrecipes.core.space.Position;
import ca.bradj.roomrecipes.logic.InclusiveSpaces;
import ca.bradj.roomrecipes.rooms.XWall;
import ca.bradj.roomrecipes.rooms.ZWall;
import ca.bradj.roomrecipes.serialization.MCRoom;
import com.google.common.collect.ImmutableList;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.network.chat.TranslatableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.*;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.FarmBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.ToolActions;
import net.minecraftforge.network.NetworkHooks;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class FarmerJob implements Job<MCHeldItem, FarmerJournal.Snapshot<MCHeldItem>>, LockSlotHaver, ContainerListener, GathererJournal.ItemsListener<MCHeldItem> {
    private final ArrayList<DataSlot> locks = new ArrayList<>();
    private ArrayList<StatusListener> statusListeners = new ArrayList<>();
    private final Container inventory;
    private Signals signal;
    private FarmerJournal<MCTownItem, MCHeldItem> journal;
    private int ticksSinceLastFarmAction;
    private MCRoom selectedFarm;
    private WorkSpot workSpot;

    public FarmerJob(
            @Nullable ServerLevel level,
            int inventoryCapacity
    ) {
        // TODO: This is copy pasted. Reduce duplication.
        SimpleContainer sc = new SimpleContainer(inventoryCapacity) {
            @Override
            public int getMaxStackSize() {
                return 1;
            }
        };
        this.inventory = sc;
        sc.addListener(this);

        for (int i = 0; i < inventoryCapacity; i++) {
            this.locks.add(new LockSlot(i, this));
        }

        this.journal = new FarmerJournal<>(
                () -> this.signal, inventoryCapacity,
                MCHeldItem::Air
        );
        this.journal.addItemListener(this);
    }

    @Override
    public void addStatusListener(StatusListener o) {
        this.statusListeners.add(o);
    }

    @Override
    public GathererJournal.Status getStatus() {
        return journal.getStatus();
    }

    @Override
    public void initializeStatus(GathererJournal.Status s) {
        Questown.LOGGER.debug("Initialized journal to state {}", s);
        this.journal.initializeStatus(s);
    }

    @Override
    public void itemsChanged(ImmutableList<MCHeldItem> items) {
        Jobs.handleItemChanges(inventory, items);
    }

    private static class WorkSpot {
        public WorkSpot(
                @NotNull BlockPos position,
                @NotNull FarmerAction action
        ) {
            this.position = position;
            this.action = action;
        }

        private final BlockPos position;
        private final FarmerAction action;

        @Override
        public String toString() {
            return "WorkSpot{" +
                    "position=" + position +
                    ", action=" + action +
                    '}';
        }
    }

    @Override
    public void tick(
            TownInterface town,
            BlockPos entityPos,
            Direction facingPos
    ) {
        ServerLevel sl = town.getServerLevel();
        if (town == null || sl == null) {
            return;
        }
        boolean isInFarm = false;
        if (selectedFarm != null) {
            isInFarm = entityPos.equals(getGateInteractionSpot(town, selectedFarm));
            if (!isInFarm) {
                isInFarm = InclusiveSpaces.contains(selectedFarm.getSpaces(), Positions.FromBlockPos(entityPos));
            }
        }
        processSignal(sl, this, isInFarm);

        if (getStatus() == GathererJournal.Status.FARMING) {
            Iterator<FarmerAction> itemAction = journal.getItems()
                    .stream()
                    .map(FarmerJob::fromItem)
                    .filter(v -> v != FarmerAction.UNDEFINED)
                    .iterator();

            this.workSpot = null;
            while (this.workSpot == null) {
                if (!itemAction.hasNext()) {
                    break;
                }
                FarmerAction next = itemAction.next();
                if (next == FarmerAction.UNDEFINED) {
                    continue;
                }
                this.workSpot = getWorkSpot(sl, selectedFarm, next);
            }
            if (this.workSpot == null) {
                this.workSpot = getWorkSpot(sl, selectedFarm, FarmerAction.UNDEFINED);
            }
            tryFarming(town, entityPos);
        }

        // TODO: Implement all of this for cook
//        // TODO: Take cooked food to chests
//        if (successTarget != null && !successTarget.isStillValid()) {
//            successTarget = null;
//        }
//
//        // TODO: Get food for self to eat
//        if (foodTarget != null && !foodTarget.isStillValid()) {
//            foodTarget = null;
//        }
//
//        if (jobBlockTarget != null) {
//            if (brain.getOrSetKitchenLocation(entityPos) == null) {
//                jobBlockTarget = null;
//            }
//        }
//        tryDropLoot(entityPos);
//        tryTakeFood(entityPos);
    }

    private @Nullable WorkSpot getWorkSpot(
            ServerLevel level,
            MCRoom selectedFarm,
            FarmerAction farmerAction
    ) {
        WorkSpot secondChoice = null;
        for (InclusiveSpace space : selectedFarm.getSpaces()) {
            Collection<Position> posz = InclusiveSpaces.getAllEnclosedPositions(space);
            for (Position v : posz) {
                BlockPos bp = Positions.ToBlock(v, selectedFarm.yCoord);
                BlockState cropBlock = level.getBlockState(bp);
                BlockPos gp = bp.below();
                BlockState groundBlock = level.getBlockState(gp);
                FarmerAction blockAction = fromBlocks(level, gp, groundBlock, cropBlock);
                // TODO: [Optimize] Cache these values
                ImmutableList<FarmerAction> itemlessActions = ImmutableList.of(
                        FarmerAction.HARVEST,
                        FarmerAction.TILL
                );
                if (farmerAction == FarmerAction.UNDEFINED && itemlessActions.contains(blockAction)) {
                    // TODO: We might want to scan all blocks to find up to one
                    //  of each action, and then choose a block by order of
                    //  preference: Harvest > Plant > Bone > Till
                    // For now, we'll just go to the first block who can be actioned
                    return new WorkSpot(gp, blockAction);
                }
                if (blockAction == farmerAction && blockAction != FarmerAction.UNDEFINED) {
                    return new WorkSpot(gp, farmerAction);
                }
                if (secondChoice == null && itemlessActions.contains(blockAction)) {
                    secondChoice = new WorkSpot(gp, blockAction);
                }
            }
        }
        return secondChoice;
    }

    private void tryFarming(
            TownInterface town,
            BlockPos entityPos
    ) {
        if (town.getServerLevel() == null) {
            return;
        }

        ticksSinceLastFarmAction++;
        if (ticksSinceLastFarmAction < Config.FARM_ACTION_INTERVAL.get()) {
            return;
        }
        ticksSinceLastFarmAction = 0;

        if (workSpot == null || workSpot.action == FarmerAction.UNDEFINED) {
            return;
        }

        BlockPos groundPos = workSpot.position;
        if (!Jobs.isCloseTo(entityPos, groundPos)) {
            return;
        }
        BlockPos cropBlock = groundPos.above();

        switch (workSpot.action) {
            case TILL -> tryTilling(town.getServerLevel(), groundPos);
            case PLANT -> tryPlanting(town.getServerLevel(), groundPos);
            case BONE -> tryBoning(town.getServerLevel(), cropBlock);
            case HARVEST -> tryHarvestCrop(town.getServerLevel(), cropBlock);
        }
    }

    private void tryBoning(
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
                        Questown.LOGGER.debug("{} is removing {} from {}", this.getJobName(), item, inventory);
                        inventory.removeItem(i, 1);
                        break;
                    }
                }
            }
        }
    }

    private void tryHarvestCrop(
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
                bs = bs.setValue(CropBlock.AGE, 0);
                level.setBlock(cropBlock, bs, 10);
            }
        }
    }

    @Nullable
    private static void tryTilling(
            ServerLevel level,
            BlockPos groundPos
    ) {
        BlockState bs = getTilledState(level, groundPos);
        if (bs == null) return;
        level.setBlock(groundPos, bs, 11);
    }

    @Nullable
    private static BlockState getTilledState(
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
        if (bs == null) {
            return null;
        }
        return bs;
    }

    private void tryPlanting(
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
                    Questown.LOGGER.debug("{} is removing {} from {}", this.getJobName(), item, inventory);
                    inventory.removeItem(i, 1);
                    break;
                }
            }
        }
    }

    private static void processSignal(
            Level level,
            FarmerJob e,
            boolean isInFarm
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

        e.signal = Signals.fromGameTime(level.getDayTime());
        e.journal.tick(e, isInFarm);
    }

    @Override
    public boolean shouldDisappear(
            TownInterface town,
            Vec3 entityPosition
    ) {
        // Since cooks don't leave town. They don't need to disappear.
        return false;
    }

    @Override
    public boolean openScreen(
            ServerPlayer sp,
            VisitorMobEntity e
    ) {
        NetworkHooks.openGui(sp, new MenuProvider() {
            @Override
            public @NotNull Component getDisplayName() {
                return TextComponent.EMPTY;
            }

            @Override
            public @NotNull AbstractContainerMenu createMenu(
                    int windowId,
                    @NotNull Inventory inv,
                    @NotNull Player p
            ) {
                return new InventoryAndStatusMenu(windowId, e.getInventory(), p.getInventory(), e.getSlotLocks(), e);
            }
        }, data -> {
            data.writeInt(journal.getCapacity());
            data.writeInt(e.getId());
            data.writeCollection(journal.getItems(), (buf, item) -> {
                ResourceLocation id = Items.AIR.getRegistryName();
                if (item != null) {
                    id = item.get().get().getRegistryName();
                }
                buf.writeResourceLocation(id);
                buf.writeBoolean(item.isLocked());
            });
        });
        return true; // Different jobs might have screens or not
    }

    @Override
    public Container getInventory() {
        return inventory;
    }

    @Override
    public FarmerJournal.Snapshot<MCHeldItem> getJournalSnapshot() {
        return journal.getSnapshot(MCHeldItem::Air);
    }

    @Override
    public void initialize(FarmerJournal.Snapshot<MCHeldItem> journal) {
        this.journal.initialize(journal);
    }

    @Override
    public ImmutableList<Boolean> getSlotLockStatuses() {
        return this.journal.getSlotLockStatuses();
    }

    @Override
    public void lockSlot(int slotIndex) {

    }

    @Override
    public void unlockSlot(int slotIndex) {

    }

    @Override
    public DataSlot getLockSlot(int i) {
        return locks.get(i);
    }

    @Override
    public @Nullable BlockPos getTarget(
            BlockPos entityPos,
            TownInterface town
    ) {
        Collection<MCRoom> farms = town.getFarms();
        if (!farms.contains(selectedFarm)) {
            selectedFarm = null;
        }

        if (journal.getStatus() == GathererJournal.Status.WALKING_TO_FARM) {
            Collection<MCRoom> rooms = farms;
            Optional<MCRoom> room = rooms.stream().findFirst();
            if (room.isPresent()) {
                this.selectedFarm = room.get();
                return getGateInteractionSpot(town, selectedFarm);
            } else {
                this.selectedFarm = null;
            }
        }

        BlockPos gateInteractionSpot = getGateInteractionSpot(town, selectedFarm);
        if (journal.getStatus() == GathererJournal.Status.FARMING) {
            if (workSpot != null) {
                return workSpot.position;
            }
            boolean inFarm = selectedFarm.yCoord == entityPos.getY() &&
                    InclusiveSpaces.contains(selectedFarm.getSpaces(), Positions.FromBlockPos(entityPos));
            if (inFarm || entityPos.equals(gateInteractionSpot)) {
                Random random = town.getServerLevel().getRandom();
                BlockPos workSpot = entityPos.relative(Direction.Plane.HORIZONTAL.getRandomDirection(random));
                if (InclusiveSpaces.contains(selectedFarm.getSpaces(), Positions.FromBlockPos(workSpot))) {
                    return workSpot;
                }
                return Positions.ToBlock(InclusiveSpaces.getMidpoint(selectedFarm.getSpace()), selectedFarm.yCoord);
            }
        }
        // TODO: Finish implementing target finding
        return null;
    }

    @Nullable
    private static BlockPos getGateInteractionSpot(
            TownInterface town,
            @Nullable MCRoom foundRoom
    ) {
        if (foundRoom == null) {
            return null;
        }
        BlockPos fencePos = Positions.ToBlock(foundRoom.getDoorPos(), foundRoom.yCoord);
        Optional<XWall> backXWall = foundRoom.getBackXWall();
        if (backXWall.isPresent()) {
            if (backXWall.get().getZ() > fencePos.getZ()) {
                return fencePos.offset(0, 0, -1);
            }
            return fencePos.offset(0, 0, 1);
        }
        Optional<ZWall> backZWall = foundRoom.getBackZWall();
        if (backZWall.isPresent()) {
            if (backZWall.get().getX() > fencePos.getX()) {
                return fencePos.offset(-1, 0, 0);
            }
            return fencePos.offset(1, 0, 0);
        }
        return fencePos.relative(Direction.Plane.HORIZONTAL.getRandomDirection(town.getServerLevel().getRandom()));
    }

    @Override
    public void initializeItems(Iterable<MCHeldItem> mcTownItemStream) {
        journal.setItems(mcTownItemStream);
    }

    @Override
    public boolean shouldBeNoClip(
            TownInterface town,
            BlockPos blockPos
    ) {
        return false;
    }

    @Override
    public Component getJobName() {
        return new TranslatableComponent("jobs.farmer");
    }

    @Override
    public boolean addToEmptySlot(MCTownItem mcTownItem) {
        boolean isAllowedToPickUp = ImmutableList.of(
                Items.WHEAT_SEEDS,
                Items.BONE_MEAL,
                Items.WHEAT
        ).contains(mcTownItem.get());
        if (!isAllowedToPickUp) {
            return false;
        }
        return journal.addItemIfSlotAvailable(new MCHeldItem(mcTownItem));
    }

    @Override
    public void containerChanged(Container p_18983_) {
        if (Jobs.isUnchanged(p_18983_, journal.getItems())) {
            return;
        }

        ImmutableList.Builder<MCHeldItem> b = ImmutableList.builder();

        for (int i = 0; i < p_18983_.getContainerSize(); i++) {
            ItemStack item = p_18983_.getItem(i);
            b.add(new MCHeldItem(MCTownItem.fromMCItemStack(item), locks.get(i).get() == 1));
        }
        journal.setItemsNoUpdateNoCheck(b.build());
    }

    private enum FarmerAction {
        TILL,
        PLANT,
        BONE,
        HARVEST,
        UNDEFINED
    }

    private static FarmerAction fromBlocks(
            ServerLevel level,
            BlockPos groundPos,
            BlockState groundState,
            BlockState cropState
    ) {
        if (getTilledState(level, groundPos) != null) {
            if (cropState.isAir()) {
                return FarmerAction.TILL;
            }
        }
        if (groundState.getBlock() instanceof FarmBlock) {
            if (!(cropState.getBlock() instanceof CropBlock)) {
                return FarmerAction.PLANT;
            }
        }
        if (cropState.getBlock() instanceof CropBlock cb) {
            if (cb.isMaxAge(cropState)) {
                return FarmerAction.HARVEST;
            }
            return FarmerAction.BONE;
        }
        return FarmerAction.UNDEFINED;
    }

    private static FarmerAction fromItem(MCHeldItem stack) {
        if (Ingredient.of(Items.BONE_MEAL).test(stack.toItem().toItemStack())) {
            return FarmerAction.BONE;
        }
        if (Ingredient.of(Items.WHEAT_SEEDS).test(stack.toItem().toItemStack())) {
            return FarmerAction.PLANT;
        }
        return FarmerAction.UNDEFINED;
    }
}
