package ca.bradj.questown.town;

import ca.bradj.questown.QT;
import ca.bradj.questown.blocks.JobBoardBlock;
import ca.bradj.questown.blocks.OpenMenuListener;
import ca.bradj.questown.gui.AddWorkContainer;
import ca.bradj.questown.gui.TownWorkContainer;
import ca.bradj.questown.gui.UIWork;
import ca.bradj.questown.jobs.JobsRegistry;
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
import java.util.Stack;
import java.util.function.Function;

public class WorkHandle implements OpenMenuListener {

    private Collection<Ingredient> requestedResults = new ArrayList<>();

    private Stack<Function<Void, Void>> nextTick = new Stack<>();

    private final TownFlagBlockEntity parent;
    private ArrayList<BlockPos> jobBoards = new ArrayList<>();

    public WorkHandle(TownFlagBlockEntity townFlagBlockEntity) {
        parent = townFlagBlockEntity;
    }

    public void registerJobBoard(ServerLevel sl, BlockPos matPos) {
        final WorkHandle self = this;
        nextTick.add(unused -> {
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

    @Override
    public void openMenuRequested(ServerPlayer sp) {
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
                AddWorkContainer r = new AddWorkContainer(windowId, requestedResults);
                return new TownWorkContainer(windowId, requestedResults.stream().map(UIWork::new).toList(), r);
            }
        }, data -> {
            AddWorkContainer.writeWorkResults(JobsRegistry.getAllOutputs(), data);
            TownWorkContainer.writeWork(requestedResults, data);
        });
    }

    public ImmutableList<Ingredient> getRequestedResults() {
        return ImmutableList.copyOf(requestedResults);
    }

    public void addWork(Collection<Ingredient> requestedResult) {
        // TODO: Add desired quantity of product to work
        this.requestedResults.addAll(requestedResult);
        QT.FLAG_LOGGER.debug("Request added to job board: {}", requestedResult);
    }

    public void tick() {
        if (this.nextTick.isEmpty()) {
            return;
        }
        this.nextTick.pop().apply(null);
    }
}
