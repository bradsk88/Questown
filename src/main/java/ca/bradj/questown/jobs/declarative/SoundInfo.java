package ca.bradj.questown.jobs.declarative;

import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

public record SoundInfo(
        ResourceLocation sound,
        @Nullable Integer chance,
        @Nullable Integer duration
) {
    public SoundInfo(ResourceLocation sound, @Nullable Integer chance, @Nullable Integer duration) {
        this.sound = sound;
        this.chance = chance;
        this.duration = duration;
        if (duration != null && duration <= 0) {
            throw new IllegalArgumentException("Duration cannot be zero. If unsure, provide null.");
        }
    }

    public static SoundInfo everyInterval(ResourceLocation location) {
        return new SoundInfo(location, 100, null);
    }

    public static SoundInfo guaranteed(
            ResourceLocation location,
            int cooldown
    ) {
        return new SoundInfo(location, 100, cooldown);
    }
}
