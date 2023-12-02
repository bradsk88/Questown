package ca.bradj.questown.town;

import ca.bradj.questown.QT;
import ca.bradj.questown.blocks.JobBoardBlock;
import ca.bradj.questown.blocks.OpenMenuListener;
import ca.bradj.questown.gui.AddWorkContainer;
import ca.bradj.questown.gui.TownWorkContainer;
import ca.bradj.questown.gui.UIWork;
import ca.bradj.questown.jobs.JobsRegistry;
import ca.bradj.questown.jobs.requests.WorkRequest;
import com.google.common.collect.ImmutableList;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextComponent;
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
import net.minecraftforge.network.NetworkHooks;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Stack;
import java.util.function.Consumer;

public class TownWorkHandle implements WorkHandle, OpenMenuListener {

    final Collection<WorkRequest> requestedResults = new ArrayList<>();

    private final Stack<Consumer<ServerLevel>> nextTick = new Stack<>();

    final ArrayList<BlockPos> jobBoards = new ArrayList<>();
    private final List<Runnable> listeners = new ArrayList<>();
    private final BlockPos parentPos;

    public TownWorkHandle(BlockPos parentPos) {
        this.parentPos = parentPos;
    }

    public void registerJobBoard(BlockPos matPos) {
        nextTick.add(sl -> {
            BlockState bs = sl.getBlockState(matPos);
            if (!(bs.getBlock() instanceof JobBoardBlock jbb)) {
                QT.FLAG_LOGGER.error("Registered job board was not found in world at {}", matPos);
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
            return;
        });
    }

    public boolean hasAtLeastOneBoard() {
        return !jobBoards.isEmpty();
    }

    @Override
    public void openMenuRequested(ServerPlayer sp) {
        BlockEntity p = sp.getLevel().getBlockEntity(parentPos);
        if (!(p instanceof TownFlagBlockEntity parent)) {
            QT.FLAG_LOGGER.error("No flag found at work handle parent pos");
            return;
        }

        BlockPos flagPos = parent.getTownFlagBasePos();
        JobsRegistry.TownData td = new JobsRegistry.TownData(parent::getAllKnownGatherResults);
        ImmutableList<Ingredient> allOutputs = JobsRegistry.getAllOutputs(td);
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
                AddWorkContainer r = new AddWorkContainer(windowId, allOutputs, flagPos);
                return new TownWorkContainer(windowId, requestedResults.stream().map(UIWork::new).toList(), r,
                        flagPos
                );
            }
        }, data -> {
            AddWorkContainer.writeWorkResults(allOutputs, data);
            AddWorkContainer.writeFlagPosition(flagPos, data);
            TownWorkContainer.writeWork(requestedResults, data);
            TownWorkContainer.writeFlagPosition(flagPos, data);
        });
    }

    public ImmutableList<WorkRequest> getRequestedResults() {
        return ImmutableList.copyOf(requestedResults);
    }

    @Override
    public void requestWork(Item requested) {
        // TODO: Take desired quantity of product as input
        WorkRequest e = WorkRequest.of(requested);
        requestWork(e);
    }

    @Override
    public void requestWork(WorkRequest e) {
        this.requestedResults.add(e);
        this.listeners.forEach(Runnable::run);
        QT.FLAG_LOGGER.debug("Request added to job board: {}", e);
    }

    @Override
    public void removeWorkRequest(WorkRequest of) {
        this.requestedResults.remove(of);
        this.listeners.forEach(Runnable::run);
        QT.FLAG_LOGGER.debug("Request removed from job board: {}", of);
    }

    public void tick(ServerLevel sl) {
        // TODO[Optimization]: Is this too much work in one tick
        jobBoards.forEach(v -> {
            BlockEntity e = sl.getBlockEntity(v);
            if (e instanceof JobBoardBlock.Entity j) {
                j.parentTick();
            }
        });
        if (this.nextTick.isEmpty()) {
            return;
        }
        this.nextTick.pop().accept(sl);
    }

    public void addChangeListener(Runnable o) {
        this.listeners.add(o);
    }
}
