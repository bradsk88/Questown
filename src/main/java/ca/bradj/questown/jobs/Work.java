package ca.bradj.questown.jobs;

import ca.bradj.questown.integration.minecraft.MCTownItem;
import ca.bradj.questown.integration.minecraft.MCTownState;
import ca.bradj.questown.town.Warper;
import com.google.common.collect.ImmutableSet;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.block.Block;

import javax.annotation.Nullable;
import java.util.Collection;
import java.util.function.Function;
import java.util.function.Predicate;

public record Work(
        @Nullable JobID parentID,
        ItemStack icon,
        WorksBehaviour.JobFunc jobFunc,
        WorksBehaviour.SnapshotFunc snapshotFunc,
        Predicate<Block> isJobBlock,
        ResourceLocation baseRoom,
        IStatus<?> initialStatus,
        Function<WorksBehaviour.TownData, ImmutableSet<MCTownItem>> results,
        ItemStack initialRequest,
        Function<IStatus<?>, Collection<Ingredient>> needs,
        Function<WorksBehaviour.WarpInput, Warper<ServerLevel, MCTownState>> warper
) {
}
