package ca.bradj.questown.jobs;

import ca.bradj.questown.integration.minecraft.MCHeldItem;
import com.google.common.collect.ImmutableList;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.Collection;

public record WorkWorldInteractions(
        int actionDuration,
        ResultGenerator<MCHeldItem> resultGenerator
) {


    private static final Collection<ItemStack> RESULTS = ImmutableList.of(Items.AIR.getDefaultInstance());
    public static final ResultGenerator<MCHeldItem> ALWAYS_EMPTY_RESULT_GENERATOR = new ResultGenerator<>() {
        @Override
        public Iterable<MCHeldItem> generate(
                ServerLevel level,
                Collection<MCHeldItem> heldItems
        ) {
            return MCHeldItem.fromMCItemStacks(RESULTS);
        }

        @Override
        public boolean isResultAlwaysEmpty() {
            return true;
        }
    };
}
