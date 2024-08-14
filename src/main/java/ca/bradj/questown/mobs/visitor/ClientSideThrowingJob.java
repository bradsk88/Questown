package ca.bradj.questown.mobs.visitor;

import ca.bradj.questown.integration.minecraft.MCHeldItem;
import ca.bradj.questown.jobs.*;
import ca.bradj.questown.town.interfaces.TownInterface;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Function;

public class ClientSideThrowingJob implements Job<MCHeldItem, ImmutableSnapshot<MCHeldItem, ?>, IStatus<?>> {
    public static final ClientSideThrowingJob INSTANCE = new ClientSideThrowingJob();

    @Override
    public Function<Void, Void> addStatusListener(StatusListener o) {
        throw new UnsupportedOperationException("Should only be called on server side");
    }

    @Override
    public IStatus<?> getStatus() {
        throw new UnsupportedOperationException("Should only be called on server side");
    }

    @Override
    public void tick(
            TownInterface town,
            LivingEntity entity,
            Direction facingPos
    ) {
        throw new UnsupportedOperationException("Should only be called on server side");
    }

    @Override
    public boolean shouldDisappear(
            TownInterface town,
            Vec3 entityPosition
    ) {
        throw new UnsupportedOperationException("Should only be called on server side");
    }

    @Override
    public boolean openScreen(
            ServerPlayer sp,
            VisitorMobEntity visitorMobEntity
    ) {
        throw new UnsupportedOperationException("Should only be called on server side");
    }

    @Override
    public Container getInventory() {
        throw new UnsupportedOperationException("Should only be called on server side");
    }

    @Override
    public ImmutableSnapshot<MCHeldItem, ?> getJournalSnapshot() {
        return null;
    }

    @Override
    public void initialize(Snapshot<MCHeldItem> journal) {

    }

    @Override
    public List<Boolean> getSlotLockStatuses() {
        return List.of();
    }

    @Override
    public DataSlot getLockSlot(int i) {
        return null;
    }

    @Override
    public @Nullable BlockPos getTarget(
            BlockPos entityBlockPos,
            Vec3 entityPos,
            TownInterface town
    ) {
        return null;
    }

    @Override
    public void initializeItems(Iterable<MCHeldItem> itemz) {

    }

    @Override
    public boolean shouldBeNoClip(
            TownInterface town,
            BlockPos blockPos
    ) {
        return false;
    }

    @Override
    public JobName getJobName() {
        return null;
    }

    @Override
    public boolean addToEmptySlot(MCHeldItem mcTownItem) {
        return false;
    }

    @Override
    public void initializeStatusFromEntityData(@Nullable String s) {

    }

    @Override
    public String getStatusToSyncToClient() {
        return "";
    }

    @Override
    public boolean isJumpingAllowed(BlockState onBlock) {
        return false;
    }

    @Override
    public boolean isInitialized() {
        return false;
    }

    @Override
    public JobID getId() {
        return null;
    }

    @Override
    public void removeStatusListener(StatusListener inventoryAndStatusMenu) {

    }

    @Override
    public boolean shouldStandStill() {
        return false;
    }

    @Override
    public boolean canStopWorkingAtAnyTime() {
        return false;
    }

    @Override
    public Function<Void, Void> addItemInsertionListener(BiConsumer<BlockPos, MCHeldItem> listener) {
        return null;
    }

    @Override
    public Function<Void, Void> addJobCompletionListener(Runnable listener) {
        return null;
    }

    @Override
    public long getTotalDuration() {
        return 0;
    }

    @Override
    public BlockPos getLook() {
        return null;
    }

    @Override
    public boolean isWorking() {
        return false;
    }
}
