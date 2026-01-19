package ca.bradj.questown.jobs;

import ca.bradj.questown.integration.minecraft.MCContainer;
import ca.bradj.questown.integration.minecraft.MCContainerInterface;
import ca.bradj.questown.integration.minecraft.MCTownItem;
import ca.bradj.questown.jobs.leaver.ContainerTarget;
import ca.bradj.questown.town.TownContainers;
import ca.bradj.questown.town.interfaces.TownInterface;
import ca.bradj.roomrecipes.adapter.RoomRecipeMatch;
import ca.bradj.roomrecipes.serialization.MCRoom;
import com.google.common.collect.ImmutableList;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.ChestBlock;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.function.Predicate;

public class Containers {
    public static List<ContainerTarget<MCContainer, MCTownItem>> get(
            TownInterface town,
            Predicate<RoomRecipeMatch<MCRoom>> includeRoom,
            Predicate<BlockPos> isJobBlock,
            Predicate<ResourceLocation> isJobSite,
            boolean stopAfterOneFound
    ) {
        return ContainersClean.get(
                town.getRoomHandle().getMatches(includeRoom).stream()
                    .map(v -> new ContainersClean.JobSite<ContainerTarget<MCContainer, MCTownItem>>() {
                        @Override
                        public ImmutableList<ContainersClean.Block<ContainerTarget<MCContainer, MCTownItem>>> getBlocks() {
                            return v.getContainedBlocks().entrySet().stream()
                                    .map(z -> new ContainersClean.Block<ContainerTarget<MCContainer, MCTownItem>>() {
                                        @Override
                                        public boolean isAir() {
                                            return town.getServerLevel().isEmptyBlock(z.getKey());
                                        }

                                        @Override
                                        public boolean isJobBlock() {
                                            return isJobBlock.test(z.getKey());
                                        }

                                        @Override
                                        public @Nullable ContainerTarget<MCContainer, MCTownItem> asChest() {
                                            if (!(z.getValue() instanceof ChestBlock cb)) {
                                                return null;
                                            }
                                            return TownContainers.fromChestBlock(
                                                    v.room,
                                                    z.getKey(),
                                                    cb,
                                                    town.getServerLevel()
                                            );
                                        }

                                        @Override
                                        public ContainerTarget<MCContainer, MCTownItem> asContainer() {
                                            return TownContainers.fromEntity(town.getServerLevel(), z.getKey());
                                        }
                                    }).collect(ImmutableList.toImmutableList());
                        }

                        @Override
                        public boolean isJobSite() {
                            return v.getRecipeIDs().stream().anyMatch(isJobSite);
                        }
                    }).collect(ImmutableList.toImmutableList()),
                stopAfterOneFound
        );
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
