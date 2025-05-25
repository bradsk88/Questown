package ca.bradj.questown.jobs;

import ca.bradj.questown.core.Pair;
import com.google.common.collect.ImmutableMap;
import net.minecraft.resources.ResourceLocation;

public record Overrides(ImmutableMap<IStatus<?>, ResourceLocation> statusTextures,
                        ImmutableMap<IStatus<?>, Pair<String, String>> statusTextOverrides) {

    public static Overrides none() {
        return new Overrides(ImmutableMap.of(), ImmutableMap.of());
    }

}
