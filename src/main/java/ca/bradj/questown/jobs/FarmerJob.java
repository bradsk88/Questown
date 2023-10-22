package ca.bradj.questown.jobs;

import ca.bradj.questown.QT;
import ca.bradj.questown.Questown;
import ca.bradj.questown.core.Config;
import ca.bradj.questown.core.init.TagsInit;
import ca.bradj.questown.integration.minecraft.MCContainer;
import ca.bradj.questown.integration.minecraft.MCHeldItem;
import ca.bradj.questown.integration.minecraft.MCTownItem;
import ca.bradj.questown.jobs.farmer.WorldInteraction;
import ca.bradj.questown.mobs.visitor.ContainerTarget;
import ca.bradj.questown.mobs.visitor.VisitorMobEntity;
import ca.bradj.questown.town.interfaces.TownInterface;
import ca.bradj.roomrecipes.adapter.Positions;
import ca.bradj.roomrecipes.core.space.Position;
import ca.bradj.roomrecipes.logic.InclusiveSpaces;
import ca.bradj.roomrecipes.rooms.XWall;
import ca.bradj.roomrecipes.rooms.ZWall;
import ca.bradj.roomrecipes.serialization.MCRoom;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.TranslatableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.*;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ComposterBlock;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.FarmBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.apache.logging.log4j.Marker;
import org.apache.logging.log4j.MarkerManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.stream.Collectors;

import static ca.bradj.questown.jobs.farmer.WorldInteraction.getTilledState;

public class FarmerJob implements Job<MCHeldItem, FarmerJournal.Snapshot<MCHeldItem>>, LockSlotHaver, ContainerListener, GathererJournal.ItemsListener<MCHeldItem>, Jobs.LootDropper<MCHeldItem>, Jobs.ContainerItemTaker {

    private final Marker marker = MarkerManager.getMarker("Farmer");

    private final ArrayList<DataSlot> locks = new ArrayList<>();
    private final Container inventory;
    private Signals signal;
    private FarmerJournal<MCTownItem, MCHeldItem> journal;
    private WorldInteraction world;
    private MCRoom selectedFarm;
    private ContainerTarget<MCContainer, MCTownItem> successTarget;
    private ContainerTarget<MCContainer, MCTownItem> suppliesTarget;
    private boolean dropping;

    private ImmutableList<Item> holdItems = ImmutableList.of(
            Items.BONE_MEAL,
            Items.WHEAT_SEEDS
    );

    public static final ImmutableList<FarmerAction> itemlessActions = ImmutableList.of(
            FarmerAction.HARVEST,
            FarmerAction.TILL
    );

    // TODO: Move this to the town flag
    private final List<BlockPos> blockWithWeeds = new ArrayList<>();

    private final UUID ownerUUID;
    private boolean reverse;
    private Map<FarmerAction, WorkSpot<BlockPos>> workSpots = new HashMap<>();

    public FarmerJob(
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

        this.world = new WorldInteraction(inventory, journal);
    }

    @Override
    public void addStatusListener(StatusListener o) {
        this.journal.addStatusListener(o);
    }

    @Override
    public GathererJournal.Status getStatus() {
        return journal.getStatus();
    }

    @Override
    public void initializeStatus(GathererJournal.Status s) {
        QT.JOB_LOGGER.debug(marker, "Initialized journal to state {}", s);
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

    @Override
    public boolean isInventoryFull() {
        return journal.isInventoryFull();
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

        Collection<MCRoom> farms = town.getFarms();
        if (!farms.contains(selectedFarm)) {
            selectedFarm = null;
        }

        Optional<MCRoom> room = town.assignToFarm(ownerUUID);
        this.selectedFarm = room.orElse(null);
        if (selectedFarm == null) {
            this.selectedFarm = town.getBiggestFarm().orElse(null);
            if (this.selectedFarm != null) {
                this.reverse = true;
            }
        }

        if (selectedFarm != null) {
            isInFarm = entityBlockPos.equals(getGateInteractionSpot(town, selectedFarm));
            if (!isInFarm) {
                isInFarm = areAllPartsOfEntityInFarm(entityPos);
            }
        }

        setupForDropLoot(entityBlockPos, town);
        processSignal(sl, town, this, isInFarm);

        WorkSpot<BlockPos> workSpot = switch (getStatus()) {
            case FARMING_HARVESTING -> workSpots.getOrDefault(FarmerAction.HARVEST, null);
            case FARMING_BONING -> workSpots.getOrDefault(FarmerAction.BONE, null);
            case FARMING_PLANTING -> workSpots.getOrDefault(FarmerAction.PLANT, null);
            case FARMING_TILLING -> workSpots.getOrDefault(FarmerAction.TILL, null);
            case FARMING_COMPOSTING -> workSpots.getOrDefault(FarmerAction.COMPOST, null);
            case FARMING_WEEDING -> workSpots.getOrDefault(FarmerAction.WEED, null);
            // FIXME: Define a custom FarmerStatus class so we can remove this default case and benefit from compiler checks
            default -> null;
        };

        setupForGetSupplies(town, Arrays.asList(
                workSpots.getOrDefault(FarmerAction.HARVEST, null),
                workSpots.getOrDefault(FarmerAction.BONE, null),
                workSpots.getOrDefault(FarmerAction.PLANT, null),
                workSpots.getOrDefault(FarmerAction.TILL, null),
                workSpots.getOrDefault(FarmerAction.COMPOST, null)
        ).stream().filter(v -> v != null).toList());

        if (workSpot != null) {
            world.tryFarming(town, entityBlockPos, workSpot);
        } else if (getStatus().isFarmingWork()) {
            QT.JOB_LOGGER.error("Workspot is null but status is {}. This is a bug.", getStatus());
        }
        tryDropLoot(entityBlockPos);
        tryGetSupplies(entityBlockPos);
    }

    private boolean areAllPartsOfEntityInFarm(Vec3 entityPos) {
        Position p1 = new Position((int) (entityPos.x + 0.5), (int) (entityPos.z + 0.5));
        Position p2 = new Position((int) (entityPos.x + 0.5), (int) (entityPos.z - 0.5));
        Position p3 = new Position((int) (entityPos.x - 0.5), (int) (entityPos.z + 0.5));
        Position p4 = new Position((int) (entityPos.x - 0.5), (int) (entityPos.z - 0.5));
        int corner1 = InclusiveSpaces.contains(selectedFarm.getSpaces(), p1) ? 1 : 0;
        int corner2 = InclusiveSpaces.contains(selectedFarm.getSpaces(), p2) ? 1 : 0;
        int corner3 = InclusiveSpaces.contains(selectedFarm.getSpaces(), p3) ? 1 : 0;
        int corner4 = InclusiveSpaces.contains(selectedFarm.getSpaces(), p4) ? 1 : 0;
        return corner1 + corner2 + corner3 + corner4 >= 2;
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

        BlockPos supplies = suppliesTarget != null ? Positions.ToBlock(
                suppliesTarget.getInteractPosition(),
                suppliesTarget.getYPosition()
        ) : null;

        BlockPos blockPos = successTarget != null ? Positions.ToBlock(
                successTarget.getInteractPosition(),
                successTarget.getYPosition()
        ) : null;

        BlockPos out = switch (getStatus()) {
            case GOING_TO_JOBSITE, LEAVING_FARM -> getGateInteractionSpot(town, selectedFarm);
            case FARMING_HARVESTING -> workSpots.get(FarmerAction.HARVEST).position;
            case FARMING_PLANTING -> workSpots.get(FarmerAction.PLANT).position;
            case FARMING_TILLING -> workSpots.get(FarmerAction.TILL).position;
            case FARMING_COMPOSTING -> workSpots.get(FarmerAction.COMPOST).position;
            case FARMING_BONING -> workSpots.get(FarmerAction.BONE).position;
            case FARMING_WEEDING-> workSpots.get(FarmerAction.WEED).position;
            case FARMING_RANDOM_TEND -> getRandomFarmSpot(sl);
            case COLLECTING_SUPPLIES -> supplies;
            case DROPPING_LOOT -> blockPos;
            case RELAXING, IDLE, NO_SUPPLIES, NO_SPACE -> null;
            default -> throw new IllegalStateException(String.format("Unexpected status %s", getStatus()));
        };
        if (out == null) {
            QT.JOB_LOGGER.warn(marker, "Unexpectedly null target for status: {}", getStatus());
        }
        return out;
    }

    @NotNull
    private BlockPos getRandomFarmSpot(@NotNull ServerLevel sl) {
        return Positions.ToBlock(
                InclusiveSpaces.getRandomEnclosedPosition(selectedFarm.getSpace(), sl.getRandom()),
                selectedFarm.yCoord
        );
    }

    private BlockPos setupForDropLoot(
            BlockPos entityBlockPos,
            TownInterface town
    ) {
        ContainerTarget<MCContainer, MCTownItem> in = this.successTarget;
        this.successTarget = Jobs.setupForDropLoot(town, this.successTarget);
        if (this.successTarget != null) {
            return Positions.ToBlock(successTarget.getInteractPosition(), successTarget.getYPosition());
        }
        return town.getRandomWanderTarget(entityBlockPos);
    }

    private void setupForGetSupplies(
            TownInterface town,
            List<WorkSpot<BlockPos>> workSpot
    ) {
        ContainerTarget<MCContainer, MCTownItem> in = this.suppliesTarget;

        List<ImmutableSet<Item>> supplies = workSpot.stream().map(
                v -> switch (v.action) {
                    case TILL, PLANT, COMPOST -> ImmutableSet.of(Items.WHEAT_SEEDS);
                    case BONE -> ImmutableSet.of(Items.BONE_MEAL);
                    default -> ImmutableSet.<Item>of();
                }
        ).toList();

        for (ImmutableSet<Item> suppliesNeeded : supplies) {
            if (this.suppliesTarget != null) {
                if (!this.suppliesTarget.hasItem(item -> suppliesNeeded.contains(item.get()))) {
                    this.suppliesTarget = town.findMatchingContainer(item -> suppliesNeeded.contains(item.get()));
                }
            } else {
                this.suppliesTarget = town.findMatchingContainer(item -> suppliesNeeded.contains(item.get()));
            }
            if ((in == null && this.suppliesTarget != null) || (in != null && this.suppliesTarget != null && !in.equals(
                    suppliesTarget))) {
                return;
            }
        }
    }

    // TODO: Should this go on the town? (or a town helper)

    public static <W extends WorkSpot<?>> @Nullable W getWorkSpot(
            Iterable<W> spots,
            List<FarmerAction> actionsForHeldItems
    ) {
        W secondChoice = null;
        for (W spot : spots) {
            FarmerAction blockAction = spot.action;
            // TODO: [Optimize] Cache these values
            if (actionsForHeldItems.isEmpty() && itemlessActions.contains(blockAction)) {
                // TODO: We might want to scan all blocks to find up to one
                //  of each action, and then choose a block by order of
                //  preference: Harvest > Plant > Bone > Compost > Till
                // For now, we'll just go to the first block who can be actioned
                return spot;
            }
            if (!actionsForHeldItems.isEmpty()) {
                if (actionsForHeldItems.get(0) == blockAction && blockAction != FarmerAction.UNDEFINED) {
                    return spot;
                }
            }
            if (actionsForHeldItems.contains(blockAction) && blockAction != FarmerAction.UNDEFINED) {
                if (secondChoice == null || spot.action.isPreferableTo(secondChoice.action)) {
                    secondChoice = spot;
                }
            }
            if (secondChoice == null && itemlessActions.contains(blockAction)) {
                secondChoice = spot;
            }
        }
        return secondChoice;
    }

    Collection<WorkSpot<BlockPos>> listAllWorkspots(
            ServerLevel level,
            @Nullable MCRoom farm
    ) {
        if (farm == null) {
            return ImmutableList.of();
        }

        return farm.getSpaces().stream()
                .flatMap(space -> InclusiveSpaces.getAllEnclosedPositions(space).stream()
                        .map(position -> {
                            BlockPos bp = Positions.ToBlock(position, farm.yCoord);
                            BlockState cropBlock = level.getBlockState(bp);
                            BlockPos gp = bp.below();
                            BlockState groundBlock = level.getBlockState(gp);
                            if (level.getRandom().nextInt(Config.FARMER_WEEDS_RARITY.get()) == 0) {
                                blockWithWeeds.add(gp);
                            }
                            FarmerAction blockAction = fromBlocks(level, gp, groundBlock, cropBlock, blockWithWeeds);
                            int score = score(groundBlock, cropBlock);
                            return new WorkSpot<>(gp, blockAction, score);
                        }))
                .sorted(Comparator.comparingInt(WorkSpot::getScore))
                .collect(Collectors.collectingAndThen(Collectors.toList(), list -> {
                    if (!this.reverse) {
                        Collections.reverse(list);
                    }
                    return list;
                }));
    }

    private int score(
            BlockState groundBlock,
            BlockState cropBlock
    ) {
        if (groundBlock.is(Blocks.FARMLAND) && cropBlock.is(Blocks.WHEAT)) {
            float value = cropBlock.getValue(CropBlock.AGE);
            float max = CropBlock.MAX_AGE;
            return (int) (100 * value / max);
        }
        return 0;
    }

    private void processSignal(
            ServerLevel level,
            TownInterface town,
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

        workSpots.clear();
        Collection<WorkSpot<BlockPos>> spots = listAllWorkspots(level, selectedFarm);

        ImmutableMap.Builder<FarmerAction, Boolean> b = ImmutableMap.builder();
        ImmutableList.copyOf(FarmerAction.values()).forEach(
                v -> {
                    WorkSpot<BlockPos> ws = getWorkSpot(spots, ImmutableList.of(v));
                    b.put(v, ws != null);
                    if (ws != null) {
                        workSpots.put(v, ws);
                    }
                }
        );
        ImmutableMap<FarmerAction, Boolean> possibleWork = b.build();

        e.signal = Signals.fromGameTime(level.getDayTime());
        e.journal.tick(
                new TownProvider() {
                    @Override
                    public boolean hasSupplies() {
                        boolean hasSeeds = town.findMatchingContainer(c -> Items.WHEAT_SEEDS.equals(c.get())) != null;
                        boolean hasBoneMeal = town.findMatchingContainer(c -> Items.BONE_MEAL.equals(c.get())) != null;
                        return hasSeeds || hasBoneMeal;
                    }

                    @Override
                    public boolean hasSpace() {
                        return town.findMatchingContainer(MCTownItem::isEmpty) != null;
                    }
                },
                new FarmerStatuses.FarmStateProvider() {
                    @Override
                    public boolean isWorkPossible(FarmerAction action) {
                        return Boolean.TRUE.equals(possibleWork.getOrDefault(action, false));
                    }
                },
                new EntityInvStateProvider() {
                    @Override
                    public boolean inventoryFull() {
                        return journal.isInventoryFull();
                    }

                    @Override
                    public boolean hasNonSupplyItems() {
                        return journal.hasAnyLootToDrop();
                    }

                    @Override
                    public Map<GathererJournal.Status, Boolean> getSupplyItemStatus() {
                        boolean hasSeeds = journal.getItems().stream().anyMatch(v -> Items.WHEAT_SEEDS.equals(v.get().get()));
                        boolean hasCompostable = journal.getItems().stream().anyMatch(
                                v -> Ingredient.of(TagsInit.Items.COMPOSTABLE).test(v.get().toItemStack()));
                        boolean hasBoneMeal = journal.getItems().stream().anyMatch(v -> Items.BONE_MEAL.equals(v.get().get()));
                        return ImmutableMap.of(
                                GathererJournal.Status.FARMING_TILLING, hasSeeds,
                                GathererJournal.Status.FARMING_PLANTING, hasSeeds,
                                GathererJournal.Status.FARMING_BONING, hasBoneMeal,
                                GathererJournal.Status.FARMING_COMPOSTING, hasCompostable
                        );
                    }

                    @Override
                    public boolean hasItems() {
                        return !journal.isInventoryEmpty();
                    }
                },
                isInFarm
        );
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
        return Jobs.openInventoryAndStatusScreen(journal.getCapacity(), journal.getItems(), sp, e);
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
    public TranslatableComponent getJobName() {
        return new TranslatableComponent("jobs.farmer");
    }

    @Override
    public boolean addToEmptySlot(MCTownItem mcTownItem) {
        boolean isAllowedToPickUp = ImmutableList.of(
                Items.WHEAT_SEEDS,
                Items.GRASS,
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

    public enum FarmerAction {
        TILL,
        PLANT,
        BONE,
        HARVEST,
        COMPOST,
        UNDEFINED, WEED;

        public boolean isPreferableTo(FarmerAction action) {
            return switch (this) {
                case TILL -> ImmutableList.of(COMPOST, UNDEFINED).contains(action);
                case PLANT -> true;
                case BONE -> true;
                case HARVEST -> true;
                case WEED -> true;
                case COMPOST -> ImmutableList.of(UNDEFINED).contains(action);
                case UNDEFINED -> false;
            };
        }

        ;
    }

    private static FarmerAction fromBlocks(
            ServerLevel level,
            BlockPos groundPos,
            BlockState groundState,
            BlockState cropState,
            List<BlockPos> blocksWithWeeds
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
            if (blocksWithWeeds.contains(groundPos)) {
                return FarmerAction.WEED;
            }
            if (level.getRandom().nextInt(Config.FARMER_WEEDS_RARITY.get()) == 0) {
                blocksWithWeeds.add(groundPos);
                return FarmerAction.WEED;
            }
            return FarmerAction.BONE;
        }
        if (cropState.getBlock() instanceof ComposterBlock) {
            if (cropState.getValue(ComposterBlock.LEVEL) >= ComposterBlock.MAX_LEVEL) {
                return FarmerAction.HARVEST;
            }
            return FarmerAction.COMPOST;
        }
        return FarmerAction.UNDEFINED;
    }
}
