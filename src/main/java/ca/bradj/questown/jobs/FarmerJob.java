package ca.bradj.questown.jobs;

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
import net.minecraft.world.*;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.ToolActions;
import net.minecraftforge.network.NetworkHooks;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class FarmerJob implements Job<MCHeldItem, FarmerJournal.Snapshot<MCHeldItem>>, LockSlotHaver, ContainerListener {
    private final ArrayList<DataSlot> locks = new ArrayList<>();
    private ArrayList<StatusListener> statusListeners = new ArrayList<>();
    private final Container inventory;
    private Signals signal;
    private FarmerJournal<MCTownItem, MCHeldItem> journal;
    private int ticksSinceLastFarmAction;

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
        // TODO: Implement
        return;
    }

    @Override
    public void tick(
            TownInterface town,
            BlockPos entityPos,
            BlockPos facingPos
    ) {
        if (town == null || town.getServerLevel() == null) {
            return;
        }
        processSignal(town.getServerLevel(), this);

        if (getStatus() == GathererJournal.Status.FARMING) {
            if (isInFarm(town, entityPos)) {
                tryFarming(town, facingPos);
            }
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

    private void tryFarming(TownInterface town, BlockPos facingPos) {
        ticksSinceLastFarmAction++;
        if (ticksSinceLastFarmAction < Config.FARM_ACTION_INTERVAL.get()) {
            return;
        }
        ticksSinceLastFarmAction = 0;
        BlockPos blockPos = facingPos.below();
        BlockState blockState = town.getServerLevel().getBlockState(blockPos);
        BlockHitResult bhr = new BlockHitResult(
                Vec3.atCenterOf(blockPos), Direction.UP,
                blockPos, false
        );
        if (blockState.getBlock() instanceof CropBlock cb) {
            if (cb.isMaxAge(blockState)) {
                List<ItemStack> drops = CropBlock.getDrops(blockState, town.getServerLevel(), blockPos, null);
                drops.forEach(v -> journal.addItem(MCHeldItem.fromMCItemStack(v)));
                // TODO: Handle more drops than inventory
                blockState.setValue(CropBlock.AGE, 0);
                return;
            }
            // TODO: Check inventory for bone meal
            cb.performBonemeal(town.getServerLevel(), town.getServerLevel().getRandom(), blockPos, blockState);
        }
        if (tryPlanting(town, facingPos, blockState, bhr)) {
            return;
        }
        BlockState mState = tryTilling(town, facingPos, blockState, bhr);
        if (mState != null) {
            town.getServerLevel().setBlock(blockPos, mState, 11);
        }
    }

    @Nullable
    private static BlockState tryTilling(TownInterface town, BlockPos entityPos, BlockState blockState, BlockHitResult bhr) {
        return blockState.getToolModifiedState(new UseOnContext(
                town.getServerLevel(), null, InteractionHand.MAIN_HAND,
                // TODO: Determine tool from held item
                Items.WOODEN_HOE.getDefaultInstance(), bhr
        ), ToolActions.HOE_TILL, false);
    }

    private boolean tryPlanting(TownInterface town, BlockPos facingPos, BlockState blockState, BlockHitResult bhr) {
        InteractionResult result = Items.WHEAT_SEEDS.useOn(new UseOnContext(
                town.getServerLevel(), null, InteractionHand.MAIN_HAND,
                Items.WHEAT_SEEDS.getDefaultInstance(), bhr
        ));
        return result.consumesAction();
    }

    private boolean isInFarm(TownInterface town, BlockPos entityPos) {
        Collection<MCRoom> rooms = town.getFarms();
        for (MCRoom foundRoom : rooms) {
            boolean inFarm = foundRoom.yCoord == entityPos.getY() &&
                    foundRoom.contains(Positions.FromBlockPos(entityPos));
            if (inFarm) {
                return true;
            }
        }
        return false;
    }


    private static void processSignal(
            Level level,
            FarmerJob e
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
        if (journal.getStatus() == GathererJournal.Status.FARMING) {
            Collection<MCRoom> rooms = town.getFarms();
            Optional<MCRoom> room = rooms.stream().findFirst();
            if (room.isPresent()) {
                MCRoom foundRoom = room.get();
                boolean inFarm = foundRoom.yCoord == entityPos.getY() &&
                        foundRoom.contains(Positions.FromBlockPos(entityPos));
                BlockPos gateInteractionSpot = getGateInteractionSpot(town, foundRoom, room);
                if (inFarm || entityPos.equals(gateInteractionSpot)) {
                    Random random = town.getServerLevel().getRandom();
                    InclusiveSpace space = foundRoom.getSpace();
                    Position pos = InclusiveSpaces.getRandomEnclosedPosition(space, random);
                    int farmLandY = foundRoom.yCoord + 1; // TODO: Only required while we put doors in the ground
                    return Positions.ToBlock(pos, farmLandY);
                }
                // TODO: Make this farmer open the gate and go through
                return gateInteractionSpot;
            }
        }
        // TODO: Finish implementing target finding
        return null;
    }

    @NotNull
    private static BlockPos getGateInteractionSpot(TownInterface town, MCRoom foundRoom, Optional<MCRoom> room) {
        BlockPos fencePos = Positions.ToBlock(foundRoom.getDoorPos(), foundRoom.yCoord);
        Optional<XWall> backXWall = room.get().getBackXWall();
        if (backXWall.isPresent()) {
            if (backXWall.get().getZ() > fencePos.getZ()) {
                return fencePos.offset(0, 0, -1);
            }
            return fencePos.offset(0, 0, 1);
        }
        Optional<ZWall> backZWall = room.get().getBackZWall();
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
    public boolean shouldBeNoClip(TownInterface town, BlockPos blockPos) {
        return false;
    }

    @Override
    public Component getJobName() {
        return new TranslatableComponent("jobs.farmer");
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
}
