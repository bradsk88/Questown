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
import ca.bradj.questown.gui.Ingredients;
import ca.bradj.questown.jobs.requests.BoardRequests;
import ca.bradj.questown.jobs.requests.RequestCategories;
import ca.bradj.questown.jobs.requests.WorkRequest;
import ca.bradj.questown.mc.Compat;
import ca.bradj.questown.town.WorkHandle;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import ca.bradj.questown.jobs.requests.RequestLimits;
import com.google.common.collect.ImmutableMap;
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

    final List<WorkRequest> requestedResults = new ArrayList<>();

    // Not saved: recomputed from storage (TownFlagBlockEntity.refreshStockedRequests).
    private ImmutableSet<WorkRequest> stocked = ImmutableSet.of();
    // Not saved: how much of each request storage holds now, for the board's "6/8".
    private ImmutableMap<WorkRequest, Integer> stock = ImmutableMap.of();
    // Saved (TownWorkHandleSerializer "limits"): each request's pause limit.
    final RequestLimits<WorkRequest> limits = new RequestLimits<>();

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
            if (!sl.isLoaded(matPos)) {
                // Right after a town reload the flag can tick before the board's chunk is back;
                // judging it then dropped a live board as "stale" (day1, 2026-10-18).
                QT.FLAG_LOGGER.info("Job board at {} is in an unloaded chunk; retrying registration", matPos);
                registerJobBoard(matPos);
                return;
            }
            BlockState bs = sl.getBlockState(matPos);
            if (!(bs.getBlock() instanceof JobBoardBlock jbb)) {
                // Stale registration (the board was broken and the world was
                // saved before this dropped out). Log and heal - the entry
                // falls out of persistence once it fails to re-register.
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

    /**
     * Drop a board position when its block is broken. Without this the
     * persisted registration outlives the block and every world load logs a
     * (healing) stale-registration entry until the next save.
     */
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
        BlockEntity p = sp.serverLevel().getBlockEntity(parentPos);
        if (!(p instanceof TownFlagBlockEntity parent)) {
            QT.FLAG_LOGGER.error("No flag found at work handle parent pos");
            return;
        }

        BlockPos flagPos = parent.getTownFlagBasePos();
        WorksBehaviour.TownData td = parent.getTownData();
        ImmutableSet<Ingredient> producible = ServerJobsRegistry.getAllOutputs(td, parent.getVillagerHandle()::isUnlocked);
        List<ResourceLocation> categories = offeredCategories(producible);
        // A tag product that is an offered category is drawn by the category's tile (r16 #18).
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
                        // Empty-state guidance (job-board-empty-state.md): when the board has no
                        // pending requests, hand the client the top unmet need so the screen can
                        // point at posting it instead of a vague teaser.
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
                    AddWorkContainer.writeCategories(categories, data);
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

    /**
     * The request categories ("Food", "Swords") to offer: those some unlocked job makes an item in
     * (RequestCategories). Requesting one posts a tag request.
     */
    private static @Nullable ResourceLocation tagOf(Ingredient ingredient) {
        String tag = Ingredients.getTag(ingredient);
        return tag == null ? null : new ResourceLocation(tag);
    }

    private static List<ResourceLocation> offeredCategories(ImmutableSet<Ingredient> outputs) {
        List<ItemStack> producible = outputs.stream()
                .flatMap(i -> Arrays.stream(i.getItems()))
                .toList();
        return RequestCategories.offered(producible, (stack, tag) -> stack.is(Compat.itemTag(tag)));
    }

    public ImmutableList<WorkRequest> getRequestedResults() {
        return ImmutableList.copyOf(requestedResults);
    }

    @Override
    public BoardRequests getBoardRequests() {
        return new BoardRequests(getRequestedResults(), stocked);
    }

    int stockOf(WorkRequest r) {
        return stock.getOrDefault(r, 0);
    }

    int limitOf(WorkRequest r) {
        return limits.get(r);
    }

    void setStock(ImmutableMap<WorkRequest, Integer> nowStock) {
        this.stock = nowStock;
    }

    @Override
    public void setRequestLimit(WorkRequest request, int limit) {
        int stored = limits.set(request, limit);
        QT.FLAG_LOGGER.info("Request limit set: {} pauses at {}", request, stored);
    }

    @Override
    public void adjustRequestLimit(WorkRequest request, int delta) {
        int stored = limits.adjust(request, delta);
        QT.FLAG_LOGGER.info("Request limit moved by {}: {} pauses at {}", delta, request, stored);
    }

    void setStocked(ImmutableSet<WorkRequest> nowStocked) {
        if (nowStocked.equals(stocked)) {
            return;
        }
        Sets.difference(nowStocked, stocked).forEach(r -> QT.FLAG_LOGGER.info(
                "Request paused: storage holds {} of {}", limits.get(r), r));
        // A request that left the board also leaves the set; only one still posted has resumed.
        Sets.difference(stocked, nowStocked).stream()
            .filter(requestedResults::contains)
            .forEach(r -> QT.FLAG_LOGGER.info("Request resumed: {}", r));
        this.stocked = nowStocked;
    }

    @Override
    public void requestWork(Item requested) {
        // TODO: Take desired quantity of product as input
        WorkRequest e = WorkRequest.of(requested);
        requestWork(e);
    }

    @Override
    public void requestWork(WorkRequest e) {
        // Idempotent (F2): re-requesting an item that already has a pending request does not
        // stack a duplicate. The board is a set of unfulfilled requests - a fulfilled one
        // drops off and can be re-requested then. (List.add would NOT dedupe - check first.)
        if (requestedResults.contains(e)) {
            QT.FLAG_LOGGER.debug("Request already on the board, not duplicated: {}", e);
            return;
        }
        // New requests go on top, posted or auto-posted (dev playtest 2026-10-30).
        requestedResults.add(0, e);
        this.listeners.forEach(l -> l.accept(new Change(null)));
        QT.FLAG_LOGGER.debug("Request added to job board: {}", e);
    }

    /** A saved request, re-added below those already restored (keeps the saved order). */
    void restoreRequest(WorkRequest e) {
        if (requestedResults.contains(e)) {
            return;
        }
        requestedResults.add(e);
    }

    @Override
    public void removeWorkRequest(WorkRequest of) {
        this.requestedResults.remove(of);
        this.limits.forget(of);
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
