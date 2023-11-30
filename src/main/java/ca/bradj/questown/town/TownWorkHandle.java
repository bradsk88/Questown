package ca.bradj.questown.town;

import ca.bradj.questown.QT;
import ca.bradj.questown.blocks.JobBoardBlock;
import ca.bradj.questown.blocks.OpenMenuListener;
import ca.bradj.questown.gui.AddWorkContainer;
import ca.bradj.questown.gui.TownWorkContainer;
import ca.bradj.questown.gui.UIWork;
import ca.bradj.questown.jobs.JobsRegistry;
import ca.bradj.questown.jobs.WorkRequest;
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
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.network.NetworkHooks;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Stack;
import java.util.function.Function;

public class TownWorkHandle implements OpenMenuListener {

    final Collection<WorkRequest> requestedResults = new ArrayList<>();

    private final Stack<Function<ServerLevel, Void>> nextTick = new Stack<>();

    private final TownFlagBlockEntity parent;
    final ArrayList<BlockPos> jobBoards = new ArrayList<>();
    private final List<Runnable> listeners = new ArrayList<>();

    public TownWorkHandle(TownFlagBlockEntity townFlagBlockEntity) {
        parent = townFlagBlockEntity;
    }

    public void registerJobBoard(BlockPos matPos) {
        final TownWorkHandle self = this;
        nextTick.add(sl -> {
            BlockState bs = sl.getBlockState(matPos);
            if (!(bs.getBlock() instanceof JobBoardBlock jbb)) {
                QT.FLAG_LOGGER.error("Registered job board was not found in world at {}", matPos);
                return null;
            }
            self.jobBoards.add(matPos);
            jbb.addOpenMenuListener(self);
            return null;
        });
    }

    public boolean hasAtLeastOneBoard() {
        return !jobBoards.isEmpty();
    }

    @Override
    public void openMenuRequested(ServerPlayer sp) {
        BlockPos flagPos = parent.getTownFlagBasePos();
        Collection<Ingredient> results = requestedResults.stream()
                .map(v -> v.getAllInterpretationsForGUI().stream())
                .toList();

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
                AddWorkContainer r = new AddWorkContainer(windowId, results, flagPos);
                return new TownWorkContainer(windowId, results.stream().map(UIWork::new).toList(), r,
                        flagPos
                );
            }
        }, data -> {
            JobsRegistry.TownData td = new JobsRegistry.TownData(parent::getAllKnownGatherResults);
            AddWorkContainer.writeWorkResults(JobsRegistry.getAllOutputs(td), data);
            AddWorkContainer.writeFlagPosition(flagPos, data);
            TownWorkContainer.writeWork(results, data);
            TownWorkContainer.writeFlagPosition(flagPos, data);
        });
    }

    public ImmutableList<WorkRequest> getRequestedResults() {
        return ImmutableList.copyOf(requestedResults);
    }

    public void addWork(Collection<WorkRequest> requestedResult) {
        // TODO: Add desired quantity of product to work
        this.requestedResults.addAll(requestedResult);
        this.listeners.forEach(Runnable::run);
        QT.FLAG_LOGGER.debug("Request added to job board: {}", requestedResult);
    }

    public void removeWork(WorkRequest of) {
        this.requestedResults.remove(of);
        this.listeners.forEach(Runnable::run);
        QT.FLAG_LOGGER.debug("Request removed from job board: {}", of);
    }

    public void tick(ServerLevel sl) {
        if (this.nextTick.isEmpty()) {
            return;
        }
        this.nextTick.pop().apply(sl);
    }

    public void addChangeListener(Runnable o) {
        this.listeners.add(o);
    }
}
