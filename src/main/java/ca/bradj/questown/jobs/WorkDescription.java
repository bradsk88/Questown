package ca.bradj.questown.jobs;

import net.minecraft.world.item.ItemStack;

public record WorkDescription(
        WorksBehaviour.CurrentlyPossibleResults currentlyPossibleResults,
        ItemStack initialRequest
) {
}
