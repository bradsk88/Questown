package ca.bradj.questown.jobs.declarative;

import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

public record SoundInfo(
        ResourceLocation sound,
        @Nullable Integer chance,
        @Nullable Integer duration
) {
    public static SoundInfo everyInterval(ResourceLocation location) {
        return new SoundInfo(location, 100, 0);
    }

    public static SoundInfo guaranteed(
            ResourceLocation location,
            int cooldown
    ) {
        return new SoundInfo(location, 100, cooldown);
    }
}
