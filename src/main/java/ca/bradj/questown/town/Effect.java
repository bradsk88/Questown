package ca.bradj.questown.town;

import net.minecraft.resources.ResourceLocation;

public record Effect(
        ResourceLocation effect,
        Long untilTick
) {
}
