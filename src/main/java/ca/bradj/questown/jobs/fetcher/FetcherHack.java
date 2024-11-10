package ca.bradj.questown.jobs.fetcher;

import ca.bradj.questown.core.init.items.ItemsInit;
import ca.bradj.questown.integration.minecraft.MCContainer;
import ca.bradj.questown.integration.minecraft.MCHeldItem;
import ca.bradj.questown.integration.minecraft.MCTownItem;
import ca.bradj.questown.items.StockRequestItem;
import ca.bradj.questown.jobs.JobID;
import ca.bradj.questown.jobs.leaver.ContainerTarget;
import ca.bradj.questown.jobs.requests.WorkRequest;
import ca.bradj.questown.jobs.special.IngredientsFromHeldItemLogic;
import ca.bradj.questown.town.TownContainers;
import ca.bradj.questown.town.interfaces.TownInterface;
import com.google.common.collect.ImmutableList;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;
import java.util.function.Supplier;

/**
 * @deprecated Fetcher was really hard to implement. So I hard-coded some shit. Sorryyyyyyy.
 */
public class FetcherHack {
    public static boolean isFetcher(JobID jobId) {
        return jobId.rootId().equals("organizer") && jobId.jobId().equals("fetch");
    }

    public static @Nullable ContainerTarget<MCContainer, MCTownItem> getTarget(
            TownInterface town
    ) {
        for (ContainerTarget<MCContainer, MCTownItem> chest : TownContainers.getAllContainers(
                town,
                town.getServerLevel()
        )) {
            if (containsUsableRequest(town, chest)) {
                return chest;
            }
        }
        return null;
    }

    private static boolean containsUsableRequest(
            TownInterface town,
            ContainerTarget<MCContainer, MCTownItem> chest
    ) {
        ServerLevel sl = town.getServerLevel();
        ImmutableList.Builder<Supplier<Collection<MCTownItem>>> elsewhereB = ImmutableList.builder();
        for (ContainerTarget<MCContainer, MCTownItem> otherChest : TownContainers.getAllContainers(town, sl)) {
            if (otherChest.getBlockPos().equals(chest.getBlockPos())) {
                continue;
            }
            elsewhereB.add(otherChest::getItems);
        }
        ImmutableList<Supplier<Collection<MCTownItem>>> elsewhere = elsewhereB.build();
        for (MCTownItem item : chest.getItems()) {
            if (!ItemsInit.STOCK_REQUEST.get().equals(item.get())) {
                continue;
            }
            WorkRequest request = StockRequestItem.getRequest(item.getItemNBT());
            if (IngredientsFromHeldItemLogic.ingredientsExist(
                    request,
                    (otherItem, req) -> req.asIngredient().test(otherItem.toItemStack()),
                    elsewhere
            )) {
                return true;
            }
        }
        return false;
    }

    public static Iterable<MCHeldItem> getItemsForDrop(
            Iterable<MCHeldItem> itemsForDrop,
            ContainerTarget<MCContainer, MCTownItem> successTarget
    ) {
        ImmutableList.Builder<MCHeldItem> b = ImmutableList.builder();
        itemsForDrop.forEach(v -> {
            if (!(v.get().get() instanceof StockRequestItem)) {
                b.add(v);
                return;
            }
            BlockPos jb = StockRequestItem.getJobBlock(v.get().getItemNBT());
            if (jb != null && jb.equals(successTarget.getBlockPos())) {
                b.add(v);
            }
        });
        return b.build();
    }

    public static @Nullable ContainerTarget<MCContainer, MCTownItem> getDropTargetForLoot(
            TownInterface town,
            ImmutableList<MCHeldItem> items,
            ContainerTarget<MCContainer, MCTownItem> defaultTarget
    ) {
        BlockPos sr = null;
        for (MCHeldItem item : items) {
            if (item.isEmpty()) {
                continue;
            }
            if (!(item.get().get() instanceof StockRequestItem)) {
                return defaultTarget;
            }
            BlockPos jb = StockRequestItem.getJobBlock(item.get().getItemNBT());
            if (jb == null) {
                continue;
            }
            sr = jb;
        }
        if (sr == null) {
            return defaultTarget;
        }
        BlockPos fsr = sr;
        List<ContainerTarget<MCContainer, MCTownItem>> all = TownContainers.getAllContainers(
                town, town.getServerLevel(),
                match -> match.getContainedBlocks().containsKey(fsr)
        );
        if (all.isEmpty()) {
            return null;
        }
        return all.get(0);
    }
}
