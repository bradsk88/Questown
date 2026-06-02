package ca.bradj.questown._vanilla;

import ca.bradj.questown.InventoryFullStrategy;
import ca.bradj.questown.integration.jobs.BeforeExtractEvent;
import ca.bradj.questown.integration.jobs.BeforeTickEvent;
import ca.bradj.questown.integration.jobs.JobPhaseModifier;
import ca.bradj.questown.integration.jobs.QTNativeRule;
import ca.bradj.questown.integration.minecraft.MCHeldItem;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class ChopDownTree extends JobPhaseModifier implements QTNativeRule {

    @Override
    public <CONTEXT> @Nullable CONTEXT beforeExtract(
            CONTEXT ctxInput,
            BeforeExtractEvent<CONTEXT> event
    ) {
        // Start from the real context: the base beforeExtract returns null ("didn't handle"),
        // so seeding from it would feed every chopped log to a null context and lose them.
        List<ItemStack> drops = chopFirstTree(event);
        if (drops.isEmpty()) {
            return null;
        }
        CONTEXT context = ctxInput;
        CONTEXT outContext = null;
        for (ItemStack drop : drops) {
            CONTEXT o = event.entity().tryGiveItem(
                    context, MCHeldItem.fromTown(drop), InventoryFullStrategy.REMOVE_FROM_WORLD
            );
            if (o != null) {
                context = o;
                outContext = o;
            }
        }
        return outContext;
    }

    /**
     * Chops the workspot tree if it is a log; otherwise scans the job block positions for one.
     * During warp {@code event.workSpot()} is unreliable (it can be the town origin), so — like
     * {@code HarvestCropSpecialRule} — we fall back to scanning. {@code chopTree} only chops logs,
     * so scanning non-log positions is a cheap no-op.
     */
    private <CONTEXT> List<ItemStack> chopFirstTree(BeforeExtractEvent<CONTEXT> event) {
        List<ItemStack> atWorkSpot = event.world().chopTree(event.workSpot());
        if (!atWorkSpot.isEmpty()) {
            return atWorkSpot;
        }
        for (BlockPos candidate : event.jobBlockPositions().get()) {
            List<ItemStack> drops = event.world().chopTree(candidate);
            if (!drops.isEmpty()) {
                return drops;
            }
        }
        return List.of();
    }

    @Override
    public void beforeTick(BeforeTickEvent bxEvent) {
        super.beforeTick(bxEvent);
        // TODO: Clear leaves that are blocking the path to the trunk
    }
}
