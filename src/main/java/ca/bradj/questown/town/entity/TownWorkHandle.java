package ca.bradj.questown.town.entity;

import ca.bradj.questown.QT;
import ca.bradj.questown.blocks.JobBoardBlock;
import ca.bradj.questown.blocks.OpenMenuListener;
import ca.bradj.questown.blocks.TownFlagSubBlocks;
import ca.bradj.questown.gui.AddWorkContainer;
import ca.bradj.questown.gui.TownWorkContainer;
import ca.bradj.questown.gui.UIWork;
import ca.bradj.questown.jobs.ServerJobsRegistry;
import ca.bradj.questown.jobs.WorksBehaviour;
import ca.bradj.questown.jobs.requests.WorkRequest;
import ca.bradj.questown.mc.Compat;
import ca.bradj.questown.town.WorkHandle;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Stack;
import java.util.function.Consumer;

public class TownWorkHandle implements WorkHandle, OpenMenuListener {

    private boolean initialized;

    void setInitialized() {
        this.initialized = true;
    }

    public record Change(
            @Nullable WorkRequest removed
    ) {
    }

    final Collection<WorkRequest> requestedResults = new ArrayList<>();

    private final Stack<Consumer<ServerLevel>> nextTick = new Stack<>();

    final ArrayList<BlockPos> jobBoards = new ArrayList<>();
    private final List<Consumer<Change>> listeners = new ArrayList<>();
    private final BlockPos parentPos;
    private final TownFlagSubBlocks subBlocks;

    public TownWorkHandle(
            TownFlagSubBlocks subBlocks,
            BlockPos parentPos
    ) {
        this.parentPos = parentPos;
        this.subBlocks = subBlocks;
    }

    public void registerJobBoard(BlockPos matPos) {
        nextTick.add(sl -> {
            BlockState bs = sl.getBlockState(matPos);
            if (!(bs.getBlock() instanceof JobBoardBlock jbb)) {
                // A registration with no board (it was broken): log it; it drops out at the next save.
                QT.FLAG_LOGGER.info(
                        "Job board registration at {} no longer matches a board in the world; dropping stale entry",
                        matPos
                );
                return;
            }

            BlockEntity p = sl.getBlockEntity(parentPos);
            if (!(p instanceof TownFlagBlockEntity parent)) {
                QT.FLAG_LOGGER.error("No flag found at work handle parent pos");
                return;
            }

            TownWorkHandle self = parent.workHandle;

            self.jobBoards.add(matPos);
            jbb.addOpenMenuListener(self);
            subBlocks.register(matPos);
        });
    }

    /** Forgets a broken job board. */
    public void removeJobBoard(BlockPos matPos) {
        jobBoards.remove(matPos);
    }

    @Override
    public boolean hasAtLeastOneBoard() {
        return !jobBoards.isEmpty();
    }

    @Override
    public void openMenuRequested(
            ServerPlayer sp,
            boolean skipToAdd
    ) {
        BlockEntity p = sp.getLevel().getBlockEntity(parentPos);
        if (!(p instanceof TownFlagBlockEntity parent)) {
            QT.FLAG_LOGGER.error("No flag found at work handle parent pos");
            return;
        }

        BlockPos flagPos = parent.getTownFlagBasePos();
        WorksBehaviour.TownData td = parent.getTownData();
        ImmutableSet<Ingredient> producible = ServerJobsRegistry.getAllOutputs(td, parent.getVillagerHandle()::isUnlocked);
        List<ResourceLocation> categories = offeredCategories(producible);
        ImmutableSet<Ingredient> allOutputs = ImmutableSet.copyOf(RequestCategories.withoutCategories(
                producible, TownWorkHandle::tagOf, categories));
        Compat.openScreen(
                sp, new MenuProvider() {
                    @Override
                    public @NotNull Component getDisplayName() {
                        return Compat.literal("");
                    }

                    @Override
                    public @NotNull AbstractContainerMenu createMenu(
                            int windowId,
                            @NotNull Inventory inv,
                            @NotNull Player p
                    ) {
                        AddWorkContainer r = new AddWorkContainer(windowId, allOutputs, categories, flagPos);
                        // With no requests, send the top missing item for the empty board's hint.
                        String topNeed = requestedResults.isEmpty()
                                ? (parent.topUnmetNeedName() == null ? "" : parent.topUnmetNeedName())
                                : "";
                        return new TownWorkContainer(
                                windowId, requestedResults.stream().map(w -> new UIWork(w, stocked.contains(w), stockOf(w), limits.get(w))).toList(), r,
                                flagPos, skipToAdd, parent.isBoardAutoRequest(), topNeed
                        );
                    }
                }, data -> {
                    AddWorkContainer.writeWorkResults(allOutputs, data);
                    AddWorkContainer.writeFlagPosition(flagPos, data);
                    TownWorkContainer.writeWork(requestedResults, stocked, this::stockOf, limits::get, data);
                    TownWorkContainer.writeFlagPosition(flagPos, data);
                    data.writeBoolean(skipToAdd);
                    data.writeBoolean(parent.isBoardAutoRequest());
                    data.writeUtf(requestedResults.isEmpty()
                            ? (parent.topUnmetNeedName() == null ? "" : parent.topUnmetNeedName())
                            : "");
                }
        );
    }

    public ImmutableList<WorkRequest> getRequestedResults() {
        return ImmutableList.copyOf(requestedResults);
    }

    @Override
    public void adjustRequestLimit(WorkRequest request, int delta) {
        int stored = limits.adjust(request, delta);
        QT.FLAG_LOGGER.info("Request limit moved by {}: {} pauses at {}", delta, request, stored);
    }

    @Override
    public void requestWork(Item requested) {
        // TODO: Take desired quantity of product as input
        WorkRequest e = WorkRequest.of(requested);
        requestWork(e);
    }

    @Override
    public void requestWork(WorkRequest e) {
        // A request already on the board isn't added again.
        if (requestedResults.contains(e)) {
            QT.FLAG_LOGGER.debug("Request already on the board, not duplicated: {}", e);
            return;
        }
        // New requests go on top.
        requestedResults.add(0, e);
        this.listeners.forEach(l -> l.accept(new Change(null)));
        QT.FLAG_LOGGER.debug("Request added to job board: {}", e);
    }

    void restoreRequest(WorkRequest e) {
        if (requestedResults.contains(e)) {
            return;
        }
        requestedResults.add(e);
    }
    @Override
    public void removeWorkRequest(WorkRequest of) {
        this.requestedResults.remove(of);
        this.listeners.forEach(l -> l.accept(new Change(of)));
        QT.FLAG_LOGGER.debug("Request removed from job board: {}", of);
    }

    public void tick(ServerLevel sl) {
        if (this.nextTick.isEmpty()) {
            return;
        }
        this.nextTick.pop().accept(sl);
    }

    public void addChangeListener(Consumer<Change> o) {
        this.listeners.add(o);
    }
}
