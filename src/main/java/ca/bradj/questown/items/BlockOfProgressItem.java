package ca.bradj.questown.items;

import ca.bradj.questown.blocks.BlockOfProgress;
import ca.bradj.questown.core.init.BlocksInit;
import ca.bradj.questown.mc.Compat;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * The Block of Progress item. A "gold" (blessed) stack calls itself a Gold Block of Progress and
 * carries a guaranteed root job (see {@link BlockOfProgress#bless(ItemStack, ca.bradj.questown.jobs.JobID)}),
 * shown as secondary text: "Guaranteed to change job to <root>".
 */
public class BlockOfProgressItem extends BlockItem {

    public BlockOfProgressItem(Properties props) {
        super(BlocksInit.BLOCK_OF_PROGRESS.get(), props);
    }

    @Override
    public Component getName(ItemStack stack) {
        if (BlockOfProgress.isBlessed(stack)) {
            return Compat.translatable("block.questown.block_of_progress.blessed");
        }
        return super.getName(stack);
    }

    @Override
    public void appendHoverText(
            ItemStack stack,
            @Nullable Level level,
            List<Component> tooltip,
            TooltipFlag flag
    ) {
        super.appendHoverText(stack, level, tooltip, flag);
        BlockOfProgress.getBlessedJob(stack)
                       .ifPresent(job -> tooltip.add(Compat.translatableStyled(
                               "tooltip.block_of_progress.guaranteed_job",
                               Style.EMPTY.withColor(ChatFormatting.GOLD),
                               Compat.translatable("jobs." + job.rootId())
                       )));
    }
}
