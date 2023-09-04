package ca.bradj.questown.mobs.visitor;

import ca.bradj.questown.Questown;
import ca.bradj.questown.core.init.TagsInit;
import ca.bradj.questown.gui.InventoryAndStatusMenu;
import ca.bradj.questown.integration.minecraft.MCContainer;
import ca.bradj.questown.integration.minecraft.MCHeldItem;
import ca.bradj.questown.integration.minecraft.MCTownItem;
import ca.bradj.questown.jobs.*;
import ca.bradj.questown.town.interfaces.TownInterface;
import ca.bradj.roomrecipes.adapter.Positions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.network.chat.TranslatableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerListener;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkHooks;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class GathererJob implements Job<MCHeldItem, GathererJournal.Snapshot<MCHeldItem>>, GathererJournal.SignalSource, GathererJournal.LootProvider<MCTownItem>, ContainerListener, GathererJournal.ItemsListener<MCHeldItem>, LockSlotHaver {

    private final @Nullable ServerLevel level;
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
            @Nullable ServerLevel level,
            int inventoryCapacity,
            UUID ownerUUID
    ) {
        this.level = level;
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

        GathererStatuses.TownStateProvider town = new GathererStatuses.TownStateProvider() {
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
                town, inventoryCapacity, GathererJob::checkTools
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
            BlockPos entityPos,
            BlockPos relative) {
        if (town == null || town.getServerLevel() == null) {
            return;
        }
        processSignal(town.getServerLevel(), this);

        this.closeToGate = false;
        BlockPos welcomeMat = town.getClosestWelcomeMatPos(entityPos);
        if (signal == Signals.MORNING && welcomeMat != null) {
            this.closeToGate = isCloseTo(entityPos, welcomeMat);
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
        return getLootFromLevel(level, journal.getCapacity(), tools);
    }

    public static Collection<MCTownItem> getLootFromLevel(
            ServerLevel level,
            int maxItems,
            GathererJournal.Tools tools
    ) {
        if (level == null) {
            return ImmutableList.of();
        }

        ImmutableList.Builder<MCTownItem> items = ImmutableList.builder();
        if (tools.hasAxe()) {
            List<MCTownItem> axed = computeAxedItems(level, maxItems);
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
        return getLoots(level, minItems, maxItems, new ResourceLocation(Questown.MODID, "jobs/gatherer_vanilla"));
    }

    @NotNull
    private static List<MCTownItem> computeAxedItems(
            ServerLevel level,
            int maxItems
    ) {
        ResourceLocation rl = new ResourceLocation(Questown.MODID, "jobs/gatherer_plains_axe");
        return getLoots(level, 3, maxItems, rl);
    }

    @NotNull
    private static List<MCTownItem> computeWoodPickaxedItems(
            ServerLevel level,
            int maxItems
    ) {
        ResourceLocation rl = new ResourceLocation(Questown.MODID, "jobs/gatherer_plains_pickaxe_wood");
        return getLoots(level, 3, maxItems, rl);
    }

    @NotNull
    private static List<MCTownItem> computeWoodShoveledItems(
            ServerLevel level,
            int maxItems
    ) {
        ResourceLocation rl = new ResourceLocation(Questown.MODID, "jobs/gatherer_plains_shovel_wood");
        return getLoots(level, 3, maxItems, rl);
    }

    @NotNull
    private static List<MCTownItem> computeFishedItems(
            ServerLevel level,
            int maxItems
    ) {
        ResourceLocation rl = new ResourceLocation("minecraft", "gameplay/fishing");
        return getLoots(level, 3, maxItems, rl);
    }

    @NotNull
    private static List<MCTownItem> getLoots(
            ServerLevel level,
            int minItems,
            int maxItems,
            ResourceLocation rl
    ) {
        if (maxItems <= 0) {
            return ImmutableList.of();
        }

        LootTable lootTable = level.getServer().getLootTables().get(rl);
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

    public void initializeStatus(GathererJournal.Status status) {
        Questown.LOGGER.debug("Initialized journal to state {}", status);
        this.journal.initializeStatus(status);
    }

    public BlockPos getTarget(
            BlockPos entityPos,
            TownInterface town
    ) {
        BlockPos enterExitPos = getEnterExitPos(town); // TODO: Smarter logic? Town gate?
        return switch (journal.getStatus()) {
            case NO_FOOD -> handleNoFoodStatus(town);
            case NO_GATE -> handleNoGateStatus(entityPos, town);
            case UNSET, IDLE, STAYING, RELAXING -> null;
            case GATHERING, GATHERING_EATING, GATHERING_HUNGRY, RETURNING, RETURNING_AT_NIGHT, CAPTURED -> enterExitPos;
            case DROPPING_LOOT, RETURNED_SUCCESS, NO_SPACE -> setupForDropLoot(town);
            case RETURNED_FAILURE -> new BlockPos(town.getVisitorJoinPos());
            case FARMING -> throw new IllegalArgumentException("Gatherer was given farmer status");
        };
    }

    private BlockPos handleNoGateStatus(
            BlockPos entityPos,
            TownInterface town
    ) {
        if (journal.hasAnyLootToDrop()) {
            return setupForDropLoot(town);
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
            return town.getRandomWanderTarget();
        }
    }

    private BlockPos handleNoFoodStatus(TownInterface town) {
        if (journal.hasAnyLootToDrop()) {
            return setupForDropLoot(town);
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
            return town.getRandomWanderTarget();
        }
    }

    private BlockPos setupForDropLoot(TownInterface town) {
        Questown.LOGGER.debug("Visitor is searching for chest space");
        if (this.successTarget != null) {
            if (!this.successTarget.hasItem(MCTownItem::isEmpty)) {
                this.successTarget = town.findMatchingContainer(MCTownItem::isEmpty);
            }
        } else {
            this.successTarget = town.findMatchingContainer(MCTownItem::isEmpty);
        }
        if (this.successTarget != null) {
            Questown.LOGGER.debug("Located chest at {}", this.successTarget.getPosition());
            return Positions.ToBlock(this.successTarget.getInteractPosition(), this.successTarget.getYPosition());
        } else {
            Questown.LOGGER.debug("No chests exist in town");
            return town.getRandomWanderTarget();
        }
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
            boolean veryCloseTo = isVeryCloseTo(entityPos, getEnterExitPos(town));
            if (veryCloseTo) {
                this.passedThroughGate = signal;
                return true;
            }
            return false;
        }
        return journal.getStatus().isReturning();
    }

    private boolean isCloseTo(
            @NotNull BlockPos entityPos,
            @NotNull BlockPos targetPos
    ) {
        double d = targetPos.distToCenterSqr(entityPos.getX(), entityPos.getY(), entityPos.getZ());
        return d < 5;
    }

    private boolean isVeryCloseTo(
            Vec3 entityPos,
            @NotNull BlockPos targetPos
    ) {
        double d = targetPos.distToCenterSqr(entityPos.x, entityPos.y, entityPos.z);
        return d < 0.5;
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
        return isCloseTo(entityPos, Positions.ToBlock(foodTarget.getPosition(), foodTarget.yPosition));
    }

    public boolean isCloseToChest(
            @NotNull BlockPos entityPos
    ) {
        if (successTarget == null) {
            return false;
        }
        if (!successTarget.hasItem(MCTownItem::isEmpty)) {
            return false;
        }
        return isCloseTo(entityPos, Positions.ToBlock(successTarget.getPosition(), successTarget.yPosition));
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
        this.dropping = true;
        if (!journal.hasAnyLootToDrop()) {
            Questown.LOGGER.trace("{} is not dropping because they only have food", ownerUUID);
            this.dropping = false;
            return;
        }
        if (!isCloseToChest(entityPos)) {
            Questown.LOGGER.trace("{} is not dropping because they are not close to an empty chest", ownerUUID);
            this.dropping = false;
            return;
        }
        List<MCHeldItem> snapshot = Lists.reverse(ImmutableList.copyOf(journal.getItems()));
        for (MCHeldItem mct : snapshot) {
            if (mct.isEmpty()) {
                continue;
            }
            // TODO: Unit tests of this logic!
            if (mct.isLocked()) {
                Questown.LOGGER.trace("Gatherer is not putting away {} because it is locked", mct);
                continue;
            }
            Questown.LOGGER.debug("Gatherer {} is putting {} in {}", ownerUUID, mct, successTarget);
            boolean added = false;
            for (int i = 0; i < successTarget.container.size(); i++) {
                if (added) {
                    break;
                }
                if (successTarget.container.getItem(i).isEmpty()) {
                    if (journal.removeItem(mct)) {
                        successTarget.container.setItem(i, mct.toItem());
                    }
                    added = true;
                }
            }
            if (!added) {
                Questown.LOGGER.debug("Nope. No space for {}", mct);
            }
        }
        this.dropping = false;
    }

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
        if (Jobs.isUnchanged(inventory, items)) {
            return;
        }

        for (int i = 0; i < items.size(); i++) {
            if (items.get(i).get().equals(inventory.getItem(i).getItem())) {
                continue;
            }
            inventory.setItem(i, new ItemStack(items.get(i).get().get(), 1));
        }
    }

    public Container getInventory() {
        return inventory;
    }

    public GathererJournal.Status getStatus() {
        return journal.getStatus();
    }

    public void addStatusListener(StatusListener l) {
        journal.addStatusListener(l);
    }

    @Override
    public void initializeItems(Iterable<MCHeldItem> mcTownItemStream) {
        journal.setItems(mcTownItemStream);
    }

    public GathererJournal.Snapshot<MCHeldItem> getJournalSnapshot() {
        return journal.getSnapshot(MCHeldItem::Air);
    }

    public void initialize(GathererJournal.Snapshot<MCHeldItem> journal) {
        this.journal.initialize(journal);
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

    public boolean shouldBeNoClip(TownInterface town, BlockPos position) {
        return this.closeToGate;
    }

    @Override
    public Component getJobName() {
        return new TranslatableComponent("jobs.gatherer");
    }


}