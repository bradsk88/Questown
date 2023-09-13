package ca.bradj.questown.jobs;

import ca.bradj.questown.Questown;
import ca.bradj.questown.core.Config;
import ca.bradj.questown.gui.InventoryAndStatusMenu;
import ca.bradj.questown.integration.minecraft.MCContainer;
import ca.bradj.questown.integration.minecraft.MCHeldItem;
import ca.bradj.questown.integration.minecraft.MCTownItem;
import ca.bradj.questown.mobs.visitor.ContainerTarget;
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
import net.minecraft.world.*;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.ComposterBlock;
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
import java.util.stream.Collectors;

public class FarmerJob implements Job<MCHeldItem, FarmerJournal.Snapshot<MCHeldItem>>, LockSlotHaver, ContainerListener, GathererJournal.ItemsListener<MCHeldItem>, Jobs.LootDropper<MCHeldItem>, Jobs.ContainerItemTaker {
    private final ArrayList<DataSlot> locks = new ArrayList<>();
    private ArrayList<StatusListener> statusListeners = new ArrayList<>();
    private final Container inventory;
    private Signals signal;
    private FarmerJournal<MCTownItem, MCHeldItem> journal;
    private int ticksSinceLastFarmAction;
    private MCRoom selectedFarm;
    private WorkSpot workSpot;
    private ContainerTarget<MCContainer, MCTownItem> successTarget;
    private ContainerTarget<MCContainer, MCTownItem> suppliesTarget;
    private boolean dropping;

    private ImmutableList<Item> holdItems = ImmutableList.of(
            Items.BONE_MEAL,
            Items.WHEAT_SEEDS
    );

    ImmutableList<FarmerAction> itemlessActions = ImmutableList.of(
            FarmerAction.HARVEST,
            FarmerAction.TILL
    );

    private final UUID ownerUUID;

    public FarmerJob(
            @Nullable ServerLevel level,
            UUID ownerUUID,
            int inventoryCapacity
    ) {
        // TODO: This is copy pasted. Reduce duplication.
        SimpleContainer sc = new SimpleContainer(inventoryCapacity) {
            @Override
            public int getMaxStackSize() {
                return 1;
            }
        };
        this.ownerUUID = ownerUUID;
        this.inventory = sc;
        sc.addListener(this);

        for (int i = 0; i < inventoryCapacity; i++) {
            this.locks.add(new LockSlot(i, this));
        }

        this.journal = new FarmerJournal<>(
                () -> this.signal, inventoryCapacity,
                (status, item) -> holdItems.contains(item.get().get()),
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

    @Override
    public UUID UUID() {
        return ownerUUID;
    }

    @Override
    public boolean hasAnyLootToDrop() {
        return journal.hasAnyLootToDrop();
    }

    @Override
    public Iterable<MCHeldItem> getItems() {
        return journal.getItems();
    }

    @Override
    public boolean removeItem(MCHeldItem mct) {
        return journal.removeItem(mct);
    }

    @Override
    public void addItem(MCHeldItem mcHeldItem) {
        journal.addItem(mcHeldItem);
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
            BlockPos entityBlockPos,
            Vec3 entityPos,
            Direction facingPos
    ) {
        ServerLevel sl = town.getServerLevel();
        if (town == null || sl == null) {
            return;
        }
        boolean isInFarm = false;
        if (selectedFarm != null) {
            isInFarm = entityBlockPos.equals(getGateInteractionSpot(town, selectedFarm));
            if (!isInFarm) {
                isInFarm = areAllPartsOfEntityInFarm(entityPos);
            }
        }
        processSignal(sl, this, isInFarm);

        tryFarming(town, entityBlockPos);
        tryDropLoot(entityBlockPos);
        tryGetSupplies(entityBlockPos);

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

    private boolean areAllPartsOfEntityInFarm(Vec3 entityPos) {
        Position p1 = new Position((int) (entityPos.x + 0.5), (int) (entityPos.z + 0.5));
        Position p2 = new Position((int) (entityPos.x + 0.5), (int) (entityPos.z - 0.5));
        Position p3 = new Position((int) (entityPos.x - 0.5), (int) (entityPos.z + 0.5));
        Position p4 = new Position((int) (entityPos.x - 0.5), (int) (entityPos.z - 0.5));
        boolean corner1 = InclusiveSpaces.contains(selectedFarm.getSpaces(), p1);
        boolean corner2 = InclusiveSpaces.contains(selectedFarm.getSpaces(), p2);
        boolean corner3 = InclusiveSpaces.contains(selectedFarm.getSpaces(), p3);
        boolean corner4 = InclusiveSpaces.contains(selectedFarm.getSpaces(), p4);
        return corner1 && corner2 && corner3 && corner4;
    }

    private void tryDropLoot(
            BlockPos entityPos
    ) {
        // TODO: Introduce this status for farmer
//        if (journal.getStatus() != GathererJournal.Status.DROPPING_LOOT) {
//            return;
//        }
        if (this.dropping) {
            Questown.LOGGER.debug("Trying to drop too quickly");
        }
        this.dropping = Jobs.tryDropLoot(this, entityPos, successTarget);
    }

    private void tryGetSupplies(
            BlockPos entityPos
    ) {
        // TODO: Introduce this status for farmer
//        if (journal.getStatus() != GathererJournal.Status.DROPPING_LOOT) {
//            return;
//        }
        Jobs.tryTakeContainerItems(this, entityPos, suppliesTarget, (item) -> holdItems.contains(item.get()));
    }

    @Override
    public @Nullable BlockPos getTarget(
            BlockPos entityBlockPos,
            Vec3 entityPos,
            TownInterface town
    ) {
        @Nullable ServerLevel sl = town.getServerLevel();
        if (sl == null) {
            return null;
        }

        Collection<MCRoom> farms = town.getFarms();
        if (!farms.contains(selectedFarm)) {
            selectedFarm = null;
        }

        Collection<MCRoom> rooms = farms;
        Optional<MCRoom> room = rooms.stream().findFirst();
        this.selectedFarm = room.orElse(null);
        if (selectedFarm == null) {
            return null;
        }

        if (journal.getStatus() == GathererJournal.Status.WALKING_TO_FARM) {
            return getGateInteractionSpot(town, selectedFarm);
        }

        if (journal.getStatus() == GathererJournal.Status.FARMING) {
            Iterator<Collection<FarmerAction>> itemAction = journal.getItems()
                    .stream()
                    .map(FarmerJob::fromItem)
                    .filter(v -> !v.isEmpty())
                    .iterator();

            Collection<WorkSpot> spots = listAllWorkspots(sl, selectedFarm);

            this.workSpot = null;
            while (this.workSpot == null) {
                if (!itemAction.hasNext()) {
                    break;
                }
                Collection<FarmerAction> next = itemAction.next();
                if (next.isEmpty()) {
                    continue;
                }
                this.workSpot = getWorkSpot(spots, next);
            }
            if (this.workSpot == null) {
                this.workSpot = getWorkSpot(spots, ImmutableList.of());
            }

            if (workSpot != null) {
                Questown.LOGGER.debug("{} using workspot with action {}", this.ownerUUID, workSpot.action);
                return workSpot.position;
            }
            Set<Item> suppliesNeeded = spots
                    .stream()
                    .map(v -> v.action)
                    .map(v -> switch (v) {
                        case PLANT, COMPOST -> Items.WHEAT_SEEDS;
                        case BONE -> Items.BONE_MEAL;
                        case TILL, HARVEST, UNDEFINED -> null;
                    })
                    .filter(Objects::nonNull)
                    .collect(Collectors.toSet());
            if (journal.isInventoryEmpty()) {
                if (areAllPartsOfEntityInFarm(entityPos)) {
                    return getGateInteractionSpot(town, selectedFarm);
                }
                setupForGetSupplies(town, suppliesNeeded);
                if (suppliesTarget != null) {
                    return Positions.ToBlock(suppliesTarget.getInteractPosition(), suppliesTarget.getYPosition());
                }
                return Positions.ToBlock(
                        InclusiveSpaces.getRandomEnclosedPosition(selectedFarm.getSpace(), sl.getRandom()),
                        selectedFarm.yCoord
                );
            }
            if (journal.getItems().stream().noneMatch(v -> suppliesNeeded.contains(v.get().get()))) {
                // TODO: Add a dropping_loot status to the journal?
                if (areAllPartsOfEntityInFarm(entityPos)) {
                    return getGateInteractionSpot(town, selectedFarm);
                }
                return setupForDropLoot(town);
            }
            return Positions.ToBlock(
                    InclusiveSpaces.getRandomEnclosedPosition(selectedFarm.getSpace(), sl.getRandom()),
                    selectedFarm.yCoord
            );
        }
        // TODO: Finish implementing target finding
        return null;
    }

    private BlockPos setupForDropLoot(TownInterface town) {
        this.successTarget = Jobs.setupForDropLoot(town, this.successTarget);
        if (this.successTarget != null) {
            return Positions.ToBlock(successTarget.getInteractPosition(), successTarget.getYPosition());
        }
        return town.getRandomWanderTarget();
    }

    private void setupForGetSupplies(
            TownInterface town,
            Set<Item> suppliesNeeded
    ) {
        Questown.LOGGER.debug("Farmer is searching for supplies");
        if (this.suppliesTarget != null) {
            if (!this.suppliesTarget.hasItem(item -> suppliesNeeded.contains(item.get()))) {
                this.suppliesTarget = town.findMatchingContainer(item -> suppliesNeeded.contains(item.get()));
            }
        } else {
            this.suppliesTarget = town.findMatchingContainer(item -> suppliesNeeded.contains(item.get()));
        }
        if (this.suppliesTarget != null) {
            Questown.LOGGER.debug("Farmer located supplies at {}", this.suppliesTarget.getPosition());
        }
    }

    // TODO: Should this go on the town? (or a town helper)

    private @Nullable WorkSpot getWorkSpot(
            Iterable<? extends WorkSpot> spots,
            Collection<FarmerAction> farmerActions
    ) {
        WorkSpot secondChoice = null;
        for (WorkSpot spot : spots) {
            FarmerAction blockAction = spot.action;
            // TODO: [Optimize] Cache these values
            if (farmerActions.isEmpty() && itemlessActions.contains(blockAction)) {
                // TODO: We might want to scan all blocks to find up to one
                //  of each action, and then choose a block by order of
                //  preference: Harvest > Plant > Bone > Compost > Till
                // For now, we'll just go to the first block who can be actioned
                return spot;
            }
            // FIXME: When the farmer is holding wheat seeds, this logic causes
            //  them to prefer composting over planting. That means they will
            //  almost NEVER plant seeds when a composter is present.
            if (farmerActions.contains(blockAction) && blockAction != FarmerAction.UNDEFINED) {
                return spot;
            }
            if (secondChoice == null && itemlessActions.contains(blockAction)) {
                secondChoice = spot;
            }
        }
        return secondChoice;
    }

    Collection<WorkSpot> listAllWorkspots(
            ServerLevel level,
            MCRoom farm
    ) {
        ImmutableList.Builder<WorkSpot> b = ImmutableList.builder();
        for (InclusiveSpace space : farm.getSpaces()) {
            Collection<Position> posz = InclusiveSpaces.getAllEnclosedPositions(space);
            for (Position v : posz) {
                BlockPos bp = Positions.ToBlock(v, farm.yCoord);
                BlockState cropBlock = level.getBlockState(bp);
                BlockPos gp = bp.below();
                BlockState groundBlock = level.getBlockState(gp);
                FarmerAction blockAction = fromBlocks(level, gp, groundBlock, cropBlock);
                // TODO: [Optimize] Cache these values
                b.add(new WorkSpot(gp, blockAction));
            }
        }
        return b.build();
    }

    private boolean tryFarming(
            TownInterface town,
            BlockPos entityPos
    ) {
        if (town.getServerLevel() == null) {
            return false;
        }

        ticksSinceLastFarmAction++;
        if (ticksSinceLastFarmAction < Config.FARM_ACTION_INTERVAL.get()) {
            return false;
        }
        ticksSinceLastFarmAction = 0;

        if (workSpot == null || workSpot.action == FarmerAction.UNDEFINED) {
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
            case HARVEST -> tryHarvestCrop(town.getServerLevel(), cropBlock);
            case COMPOST -> tryComposeSeeds(town.getServerLevel(), cropBlock);
        };
    }

    private boolean tryComposeSeeds(ServerLevel level, BlockPos cropBlock) {
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
                    return false;
                }
                level.setBlockAndUpdate(cropBlock, blockstate);
                Questown.LOGGER.debug("{} is removing {} from {}", this.getJobName(), item, inventory);
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
                        Questown.LOGGER.debug("{} is removing {} from {}", this.getJobName(), item, inventory);
                        inventory.removeItem(i, 1);
                        break;
                    }
                }
            }
        }
        return true;
    }

    private boolean tryHarvestCrop(
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
                    Questown.LOGGER.debug("{} is removing {} from {}", this.getJobName(), item, inventory);
                    inventory.removeItem(i, 1);
                    break;
                }
            }
        }
        return true;
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
        COMPOST,
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
        if (cropState.getBlock() instanceof ComposterBlock) {
            return FarmerAction.COMPOST;
        }
        return FarmerAction.UNDEFINED;
    }

    private static Collection<FarmerAction> fromItem(MCHeldItem stack) {
        if (Ingredient.of(Items.BONE_MEAL).test(stack.toItem().toItemStack())) {
            return ImmutableList.of(FarmerAction.BONE);
        }
        if (Ingredient.of(Items.WHEAT_SEEDS).test(stack.toItem().toItemStack())) {
            return ImmutableList.of(FarmerAction.PLANT, FarmerAction.COMPOST);
        }
        return ImmutableList.of();
    }
}
