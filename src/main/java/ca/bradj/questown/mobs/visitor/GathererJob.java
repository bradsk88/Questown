package ca.bradj.questown.mobs.visitor;

import ca.bradj.questown.Questown;
import ca.bradj.questown.core.init.TagsInit;
import ca.bradj.questown.integration.minecraft.MCContainer;
import ca.bradj.questown.integration.minecraft.MCHeldItem;
import ca.bradj.questown.integration.minecraft.MCTownItem;
import ca.bradj.questown.jobs.*;
import ca.bradj.questown.town.interfaces.TownInterface;
import ca.bradj.roomrecipes.adapter.Positions;
import com.google.common.collect.ImmutableList;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.TranslatableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerListener;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.LootTables;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.function.Function;

public class GathererJob implements Job<MCHeldItem, GathererJournal.Snapshot<MCHeldItem>, GathererJournal.Status>, SignalSource, GathererJournal.LootProvider<MCTownItem>, ContainerListener, JournalItemsListener<MCHeldItem>, LockSlotHaver, Jobs.LootDropper<MCHeldItem> {

    private @Nullable TownInterface town;
    private final Container inventory;
    private final UUID ownerUUID;
    @Nullable ContainerTarget<MCContainer, MCTownItem> foodTarget;
    @Nullable ContainerTarget<MCContainer, MCTownItem> successTarget;
    @Nullable BlockPos gateTarget;
    // TODO: Logic for changing jobs
    private final GathererJournal<MCTownItem, MCHeldItem> journal;
    private Signals signal;
    private boolean dropping;

    private final List<LockSlot> locks = new ArrayList<>();
    private Signals passedThroughGate = Signals.UNDEFINED;
    private boolean closeToGate;

    public GathererJob(
            TownInterface town,
            // null on client side
            int inventoryCapacity,
            UUID ownerUUID
    ) {
        if (town != null && !town.getServerLevel().isClientSide()) {
            this.town = town;
        }
        this.ownerUUID = ownerUUID;
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

        GathererStatuses.TownStateProvider tsp = new GathererStatuses.TownStateProvider() {
            @Override
            public boolean IsStorageAvailable() {
                return successTarget != null && successTarget.isStillValid();
            }

            @Override
            public boolean hasGate() {
                return gateTarget != null;
            }
        };
        journal = new GathererJournal<MCTownItem, MCHeldItem>(
                this, MCHeldItem::Air, MCHeldItem::new,
                tsp, inventoryCapacity, GathererJob::checkTools
        ) {
            @Override
            protected void changeStatus(Status s) {
                super.changeStatus(s);
                Questown.LOGGER.debug("Changed status to {}", s);
            }
        };
        journal.addItemsListener(this);
    }

    public static GathererJournal.Tools checkTools(Iterable<MCHeldItem> journalItems) {
        GathererJournal.Tools tool = new GathererJournal.Tools(false, false, false, false);
        for (MCHeldItem item : journalItems) {
            if (Ingredient.of(TagsInit.Items.AXES).test(item.get().toItemStack())) {
                tool = tool.withAxe();
            }
            if (Ingredient.of(TagsInit.Items.PICKAXES).test(item.get().toItemStack())) {
                tool = tool.withPickaxe();
            }
            if (Ingredient.of(TagsInit.Items.SHOVELS).test(item.get().toItemStack())) {
                tool = tool.withShovel();
            }
            if (Ingredient.of(TagsInit.Items.FISHING_RODS).test(item.get().toItemStack())) {
                tool = tool.withFishingRod();
            }
        }
        return tool;
    }

    private static void processSignal(
            Level level,
            GathererJob e
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
        e.journal.tick(e);
    }

    @NotNull
    private static BlockPos getEnterExitPos(TownInterface town) {
        return town.getEnterExitPos();
    }

    public void tick(
            TownInterface town,
            LivingEntity entity,
            Direction relative
    ) {
        if (town == null || town.getServerLevel() == null) {
            return;
        }
        processSignal(town.getServerLevel(), this);

        this.closeToGate = false;
        BlockPos entityPos = entity.blockPosition();
        BlockPos welcomeMat = town.getClosestWelcomeMatPos(entityPos);
        if (signal == Signals.MORNING && welcomeMat != null) {
            this.closeToGate = Jobs.isCloseTo(entityPos, welcomeMat);
        }

        if (successTarget != null && !successTarget.isStillValid()) {
            successTarget = null;
        }
        if (foodTarget != null && !foodTarget.isStillValid()) {
            foodTarget = null;
        }
        if (gateTarget != null) {
            if (welcomeMat == null) {
                gateTarget = null;
            }
        }
        tryDropLoot(entityPos);
        tryTakeFood(entityPos);
    }

    public Signals getSignal() {
        return this.signal;
    }

    public Collection<MCTownItem> getLoot(GathererJournal.Tools tools) {
        return getLootFromLevel(town, journal.getCapacity(), tools);
    }

    public static Collection<MCTownItem> getLootFromLevel(
            TownInterface town,
            int maxItems,
            GathererJournal.Tools tools
    ) {
        if (town == null || town.getServerLevel() == null) {
            return ImmutableList.of();
        }
        ServerLevel level = town.getServerLevel();

        ImmutableList.Builder<MCTownItem> items = ImmutableList.builder();
        if (tools.hasAxe()) {
            List<MCTownItem> axed = computeAxedItems(town, maxItems);
            items.addAll(axed);
            maxItems = maxItems - axed.size();
        } else if (tools.hasPick()) {
            List<MCTownItem> axed = computeWoodPickaxedItems(level, maxItems);
            items.addAll(axed);
            maxItems = maxItems - axed.size();
        } else if (tools.hasShovel()) {
            List<MCTownItem> axed = computeWoodShoveledItems(level, maxItems);
            items.addAll(axed);
            maxItems = maxItems - axed.size();
        } else if (tools.hasRod()) {
            List<MCTownItem> axed = computeFishedItems(level, maxItems);
            items.addAll(axed);
            maxItems = maxItems - axed.size();
        }
        // TODO: Handle other tool types
        else {
            // Increase the number of gathered items if no tool is carried
            items.addAll(computeGatheredItems(level, Math.min(3, maxItems), maxItems));
        }
        items.addAll(computeGatheredItems(level, Math.min(6, maxItems), maxItems));

        ImmutableList<MCTownItem> list = items.build();

        Questown.LOGGER.debug("[VMJ] Presenting items to gatherer: {}", list);

        return list;
    }

    @NotNull
    private static List<MCTownItem> computeGatheredItems(
            ServerLevel level,
            int minItems,
            int maxItems
    ) {
        ResourceLocation rl = new ResourceLocation(Questown.MODID, "jobs/gatherer_notools");
        LootTable lootTable = level.getServer().getLootTables().get(rl);
        return getLoots(level, lootTable, minItems, maxItems, rl);
    }

    @NotNull
    private static List<MCTownItem> computeAxedItems(
            TownInterface town,
            int maxItems
    ) {
        ResourceLocation biome = town.getRandomNearbyBiome();
        String id = String.format("jobs/gatherer_axe/%s/%s", biome.getNamespace(), biome.getPath());
        ResourceLocation rl = new ResourceLocation(Questown.MODID, id);
        LootTables tables = town.getServerLevel().getServer().getLootTables();
        if (!tables.getIds().contains(rl)) {
            rl = new ResourceLocation(Questown.MODID, "jobs/gatherer_axe/default");
        }
        LootTable lootTable = tables.get(rl);
        return getLoots(town.getServerLevel(), lootTable, 3, maxItems, rl);
    }

    @NotNull
    private static List<MCTownItem> computeWoodPickaxedItems(
            ServerLevel level,
            int maxItems
    ) {
        ResourceLocation rl = new ResourceLocation(Questown.MODID, "jobs/gatherer_plains_pickaxe_wood");
        LootTable lootTable = level.getServer().getLootTables().get(rl);
        return getLoots(level, lootTable, 3, maxItems, rl);
    }

    @NotNull
    private static List<MCTownItem> computeWoodShoveledItems(
            ServerLevel level,
            int maxItems
    ) {
        ResourceLocation rl = new ResourceLocation(Questown.MODID, "jobs/gatherer_plains_shovel_wood");
        LootTable lootTable = level.getServer().getLootTables().get(rl);
        return getLoots(level, lootTable, 3, maxItems, rl);
    }

    @NotNull
    private static List<MCTownItem> computeFishedItems(
            ServerLevel level,
            int maxItems
    ) {
        ResourceLocation rl = new ResourceLocation("minecraft", "gameplay/fishing");
        LootTable lootTable = level.getServer().getLootTables().get(rl);
        return getLoots(level, lootTable, 3, maxItems, rl);
    }

    @NotNull
    private static List<MCTownItem> getLoots(
            ServerLevel level,
            LootTable lootTable,
            int minItems,
            int maxItems,
            ResourceLocation rl
    ) {
        if (maxItems <= 0) {
            return ImmutableList.of();
        }

        LootContext.Builder lcb = new LootContext.Builder((ServerLevel) level);
        LootContext lc = lcb.create(LootContextParamSets.EMPTY);

        ArrayList<ItemStack> rItems = new ArrayList<>();
        int max = Math.min(minItems, level.random.nextInt(maxItems) + 1);
        while (rItems.size() < max) {
            rItems.addAll(lootTable.getRandomItems(lc));
        }
        Collections.shuffle(rItems);
        int subLen = Math.min(rItems.size(), maxItems);
        List<MCTownItem> list = rItems.stream()
                .filter(v -> !v.isEmpty())
                .map(MCTownItem::fromMCItemStack)
                .toList()
                .subList(0, subLen);
        return list;
    }

    @Override
    public void initializeStatusFromEntityData(@Nullable String s) {
        GathererJournal.Status z = GathererJournal.getStatusFromEntityData(s);
        this.journal.initializeStatus(z);
    }

    @Override
    public String getStatusToSyncToClient() {
        return getStatus().name();
    }

    public BlockPos getTarget(
            BlockPos entityBlockPos,
            Vec3 entityPos,
            TownInterface town
    ) {
        BlockPos enterExitPos = getEnterExitPos(town); // TODO: Smarter logic? Town gate?
        return switch (journal.getStatus()) {
            case NO_FOOD -> handleNoFoodStatus(entityBlockPos, town);
            case NO_GATE -> handleNoGateStatus(entityBlockPos, town);
            case UNSET, IDLE, STAYING, RELAXING -> null;
            case GATHERING, GATHERING_EATING, GATHERING_HUNGRY, RETURNING, RETURNING_AT_NIGHT, CAPTURED -> enterExitPos;
            case DROPPING_LOOT, RETURNED_SUCCESS, NO_SPACE -> setupForDropLoot(entityBlockPos, town);
            case RETURNED_FAILURE -> new BlockPos(town.getVisitorJoinPos());
            case GOING_TO_JOBSITE -> throw new IllegalArgumentException("Gatherer was job status");
            case LEAVING_FARM, FARMING_HARVESTING, FARMING_RANDOM_TEND,
                    FARMING_TILLING, FARMING_PLANTING, FARMING_BONING,
                    FARMING_COMPOSTING, FARMING_WEEDING ->
                    throw new IllegalArgumentException("Gatherer was given farmer status");
            case COLLECTING_SUPPLIES, NO_SUPPLIES, BAKING, BAKING_FUELING, COLLECTING_BREAD ->
                    throw new IllegalArgumentException("Gatherer was given baker status");
        };
    }

    private BlockPos handleNoGateStatus(
            BlockPos entityPos,
            TownInterface town
    ) {
        if (journal.hasAnyLootToDrop()) {
            return setupForDropLoot(entityPos, town);
        }

        Questown.LOGGER.debug("Visitor is searching for gate");
        if (this.gateTarget != null) {
            Questown.LOGGER.debug("Located gate at {}", this.gateTarget);
            return this.gateTarget;
        } else {
            this.gateTarget = town.getClosestWelcomeMatPos(entityPos);
        }
        if (this.gateTarget != null) {
            Questown.LOGGER.debug("Located gate at {}", this.gateTarget);
            return this.gateTarget;
        } else {
            Questown.LOGGER.debug("No gate exists in town");
            return town.getRandomWanderTarget(entityPos);
        }
    }

    private BlockPos handleNoFoodStatus(
            BlockPos entityBlockPos,
            TownInterface town
    ) {
        if (journal.hasAnyLootToDrop()) {
            return setupForDropLoot(entityBlockPos, town);
        }

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
            return Positions.ToBlock(this.foodTarget.getInteractPosition(), this.foodTarget.getYPosition());
        } else {
            Questown.LOGGER.debug("No food exists in town");
            return town.getRandomWanderTarget(entityBlockPos);
        }
    }

    private BlockPos setupForDropLoot(
            BlockPos entityPos,
            TownInterface town
    ) {
        this.successTarget = Jobs.setupForDropLoot(town, this.successTarget);
        if (this.successTarget != null) {
            return Positions.ToBlock(successTarget.getInteractPosition(), successTarget.getYPosition());
        }
        return town.getRandomWanderTarget(entityPos);
    }

    private BlockPos setupForLeaveTown(TownInterface town) {
        Questown.LOGGER.debug("Visitor is searching for a town gate");
        // TODO: Get the CLOSEST gate?
        return town.getEnterExitPos();
    }

    public boolean shouldDisappear(
            TownInterface town,
            Vec3 entityPos
    ) {
        if (passedThroughGate != Signals.UNDEFINED && passedThroughGate.equals(signal)) {
            return true;
        }
        passedThroughGate = Signals.UNDEFINED;
        if (journal.getStatus() == GathererJournal.Status.GATHERING) {
            boolean veryCloseTo = Jobs.isVeryCloseTo(entityPos, getEnterExitPos(town));
            if (veryCloseTo) {
                this.passedThroughGate = signal;
                return true;
            }
            return false;
        }
        return journal.getStatus().isReturning();
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
        return Jobs.isCloseTo(entityPos, Positions.ToBlock(foodTarget.getPosition(), foodTarget.yPosition));
    }

    public void tryTakeFood(BlockPos entityPos) {
        if (journal.getStatus() != GathererJournal.Status.NO_FOOD) {
            return;
        }
        if (journal.hasAnyFood()) {
            return;
        }
        if (!isCloseToFood(entityPos)) {
            return;
        }
        for (int i = 0; i < foodTarget.container.size(); i++) {
            MCTownItem mcTownItem = foodTarget.container.getItem(i);
            if (mcTownItem.isFood()) {
                Questown.LOGGER.debug("Gatherer is taking {} from {}", mcTownItem, foodTarget);
                journal.addItem(new MCHeldItem(mcTownItem));
                foodTarget.container.removeItem(i, 1);
                break;
            }
        }
    }

    public void tryDropLoot(
            BlockPos entityPos
    ) {
        // TODO: move to journal?
        if (journal.getStatus() != GathererJournal.Status.DROPPING_LOOT) {
            return;
        }
        if (this.dropping) {
            Questown.LOGGER.debug("Trying to drop too quickly");
        }
        this.dropping = Jobs.tryDropLoot(this, entityPos, successTarget);
    }

    public boolean openScreen(
            ServerPlayer sp,
            VisitorMobEntity e
    ) {
        return Jobs.openInventoryAndStatusScreen(journal.getCapacity(), sp, e, "gatherer");
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

    @Override
    public void itemsChanged(ImmutableList<MCHeldItem> items) {
        Jobs.handleItemChanges(inventory, items);
    }

    public Container getInventory() {
        return inventory;
    }

    public GathererJournal.Status getStatus() {
        return journal.getStatus();
    }

    @Override
    public Function<Void, Void> addStatusListener(StatusListener l) {
        return journal.addStatusListener(l);
    }

    @Override
    public boolean isJumpingAllowed(BlockState onBlock) {
        return true;
    }

    @Override
    public void initializeItems(Iterable<MCHeldItem> mcTownItemStream) {
        journal.setItems(mcTownItemStream);
    }

    public GathererJournal.Snapshot<MCHeldItem> getJournalSnapshot() {
        return journal.getSnapshot(MCHeldItem::Air);
    }

    public void initialize(Snapshot<MCHeldItem> journal) {
        this.journal.initialize((GathererJournal.Snapshot<MCHeldItem>) journal);
    }

    @Override
    public boolean isInitialized() {
        return journal.isInitialized();
    }

    public void lockSlot(int slot) {
        this.journal.lockSlot(slot);
    }

    public void unlockSlot(int slotIndex) {
        this.journal.unlockSlot(slotIndex);
    }

    @Override
    public ImmutableList<Boolean> getSlotLockStatuses() {
        return this.journal.getSlotLockStatuses();
    }

    public DataSlot getLockSlot(int i) {
        return this.locks.get(i);
    }

    public boolean shouldBeNoClip(
            TownInterface town,
            BlockPos position
    ) {
        return this.closeToGate;
    }

    @Override
    public TranslatableComponent getJobName() {
        return new TranslatableComponent("jobs.gatherer");
    }

    @Override
    public boolean addToEmptySlot(MCTownItem mcTownItem) {
        return journal.addItemIfSlotAvailable(new MCHeldItem(mcTownItem));
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
}
