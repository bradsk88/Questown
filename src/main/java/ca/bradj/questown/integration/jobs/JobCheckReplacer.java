package ca.bradj.questown.integration.jobs;

import ca.bradj.questown.integration.minecraft.MCHeldItem;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Collection;
import java.util.function.BiPredicate;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

public class JobCheckReplacer {

    private JobCheck inner;

    public JobCheckReplacer(BiPredicate<Function<BlockPos, BlockState>, BlockPos> jobBlock) {
        this.inner = (heldItems, bsf, block) -> jobBlock.test(bsf, block);
    }

    public void accept(Function<JobCheck, JobCheck> replacer) {
        this.inner = replacer.apply(inner);
    }

    public static Predicate<BlockPos> withItemsAndLevel(
            JobCheckReplacer jcr,
            Supplier<? extends Collection<MCHeldItem>> items,
            Function<BlockPos, BlockState> level
    ) {
        return p -> jcr.inner.test(items.get(), level, p);
    }
}
