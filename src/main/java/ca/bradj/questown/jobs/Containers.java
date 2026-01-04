package ca.bradj.questown.jobs;

import ca.bradj.questown.integration.minecraft.MCContainer;
import ca.bradj.questown.integration.minecraft.MCContainerInterface;
import ca.bradj.questown.integration.minecraft.MCTownItem;
import ca.bradj.questown.jobs.leaver.ContainerTarget;
import ca.bradj.questown.town.TownContainers;
import ca.bradj.questown.town.interfaces.TownInterface;
import ca.bradj.roomrecipes.adapter.RoomRecipeMatch;
import ca.bradj.roomrecipes.serialization.MCRoom;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.function.Supplier;

public class Containers {
    public static List<ContainerTarget<MCContainer, MCTownItem>> get(
            TownInterface town,
            Predicate<RoomRecipeMatch<MCRoom>> includeRoom,
            Predicate<BlockPos> isJobBlock,
            Predicate<ResourceLocation> isJobSite,
            boolean stopAfterOneFound
    ) {
        @Nullable ServerLevel sl = town.getServerLevel();
        Supplier<Collection<RoomRecipeMatch<MCRoom>>> x = () -> town.getRoomHandle().getMatches(includeRoom);
        return get2(sl, x, isJobBlock, isJobSite, stopAfterOneFound);
    }

    public static @NotNull List<ContainerTarget<MCContainer, MCTownItem>> get2(
            @Nullable ServerLevel sl,
            Supplier<? extends Collection<RoomRecipeMatch<MCRoom>>> x,
            Predicate<BlockPos> isJobBlock,
            Predicate<ResourceLocation> isJobSite,
            boolean stopAfterOneFound
    ) {
        Collection<RoomRecipeMatch<MCRoom>> allContainers = x.get();
        List<ContainerTarget<MCContainer, MCTownItem>> chests = new ArrayList<>();
        for (RoomRecipeMatch<MCRoom> c : allContainers) {
            for (Map.Entry<BlockPos, Block> block : c.getContainedBlocks().entrySet()) {
                if (block.getValue().equals(Blocks.AIR)) {
                    continue;
                }
                boolean containerIsNotInJobSite = c.getRecipeIDs().stream().noneMatch(isJobSite);
                boolean containerIsNotJobTarget = !isJobBlock.test(block.getKey());
                if (containerIsNotInJobSite || containerIsNotJobTarget) {
                    boolean added = addIfChest(c, block, sl, chests);
                    if (added && stopAfterOneFound) {
                        return chests;
                    }
                }
                if (containerIsNotJobTarget) {
                    boolean added = addIfContainer(c, block, sl, chests);
                    if (added && stopAfterOneFound) {
                        return chests;
                    }
                }
            }
        }
        return chests;
    }


    private static boolean addIfChest(
            RoomRecipeMatch<MCRoom> c,
            Map.Entry<BlockPos, Block> block,
            ServerLevel sl,
            List<ContainerTarget<MCContainer, MCTownItem>> chests
    ) {
        if (!(block.getValue() instanceof ChestBlock cb)) {
            return false;
        }
        BlockPos bp = block.getKey();
        ContainerTarget<MCContainer, MCTownItem> chest = TownContainers.fromChestBlock(c.room, bp, cb, sl);
        if (chest == null) {
            return false;
        }
        chests.add(chest);
        return true;
    }

    private static boolean addIfContainer(
            RoomRecipeMatch<MCRoom> c,
            Map.Entry<BlockPos, Block> block,
            ServerLevel sl,
            List<ContainerTarget<MCContainer, MCTownItem>> chests
    ) {
        BlockPos bp = block.getKey();
        ContainerTarget<MCContainer, MCTownItem> chest = TownContainers.fromEntity(sl, bp);
        if (chest != null) {
            chests.add(chest);
            return true;
        }
        return false;
    }

    public static int addIfPossible(
            ItemStack itemInHand,
            MCContainerInterface rack
    ) {
        if (rack.isFull()) {
            return -1;
        }
        if (!rack.canAcceptIfSpaceAllows(MCTownItem.fromMCItemStack(itemInHand))) {
            return -1;
        }
        for (int i = 0; i < rack.size(); i++) {
            if (rack.setItem(i, MCTownItem.fromMCItemStack(itemInHand))) {
                itemInHand.shrink(1);
                return i;
            }
        }
        return -1;
    }
}
