package ca.bradj.questown.jobs;

import ca.bradj.questown.integration.minecraft.MCHeldItem;
import ca.bradj.questown.mobs.visitor.VisitorMobEntity;
import ca.bradj.questown.town.interfaces.TownInterface;
import com.google.common.collect.ImmutableList;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;

public interface Job<H extends HeldItem<H, ?>, SNAPSHOT, STATUS> {
    Function<Void, Void> addStatusListener(StatusListener o);

    STATUS getStatus();

    void tick(
            TownInterface town,
            LivingEntity entity,
            Direction facingPos
    );

    boolean shouldDisappear(
            TownInterface town,
            Vec3 entityPosition
    );

    boolean openScreen(
            ServerPlayer sp,
            VisitorMobEntity visitorMobEntity
    );

    Container getInventory();

    SNAPSHOT getJournalSnapshot();

    void initialize(Snapshot<H> journal);

    List<Boolean> getSlotLockStatuses();

    DataSlot getLockSlot(int i);

    @Nullable BlockPos getTarget(
            BlockPos entityBlockPos,
            Vec3 entityPos,
            TownInterface town
    );

    void initializeItems(Iterable<H> itemz);

    boolean shouldBeNoClip(TownInterface town, BlockPos blockPos);

    JobName getJobName();

    boolean addToEmptySlot(MCHeldItem mcTownItem);

    void initializeStatusFromEntityData(@Nullable String s);

    String getStatusToSyncToClient();

    boolean isJumpingAllowed(BlockState onBlock);

    boolean isInitialized();

    JobID getId();

    void removeStatusListener(StatusListener inventoryAndStatusMenu);

    boolean shouldStandStill();

    boolean canStopWorkingAtAnyTime();

    Function<Void, Void> addItemInsertionListener(BiConsumer<BlockPos, MCHeldItem> listener);
    Function<Void, Void> addJobCompletionListener(Consumer<BlockPos> listener);

    long getTotalDuration();

    BlockPos getLook();

    ImmutableList<String> getSpecialRules(int state);

    Collection<String> getSpecialGlobalRules();
}
