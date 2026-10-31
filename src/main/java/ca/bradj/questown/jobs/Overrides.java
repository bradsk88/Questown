package ca.bradj.questown.jobs;

import org.jetbrains.annotations.Nullable;
import ca.bradj.questown.jobs.production.ProductionStatus;
import ca.bradj.questown.core.Pair;
import com.google.common.collect.ImmutableMap;
import net.minecraft.resources.ResourceLocation;

public record Overrides(ImmutableMap<IStatus<?>, ResourceLocation> statusTextures,
                        ImmutableMap<IStatus<?>, Pair<String, String>> statusTextOverrides,
                        @Nullable ResourceLocation workingTexture) {

    public Overrides(
            ImmutableMap<IStatus<?>, ResourceLocation> statusTextures,
            ImmutableMap<IStatus<?>, Pair<String, String>> statusTextOverrides
    ) {
        this(statusTextures, statusTextOverrides, null);
    }

    public static Overrides none() {
        return new Overrides(ImmutableMap.of(), ImmutableMap.of());
    }

    /**
     * The status image this job overrides, or null for the default art. A named status override
     * wins; otherwise {@link #workingTexture} covers every work state (state:N) and extraction,
     * which are not named core statuses (job JSON: {@code {"type": "working", ...}}).
     */
    public @Nullable ResourceLocation textureFor(IStatus<?> status) {
        ResourceLocation named = statusTextures.get(status);
        if (named != null || workingTexture == null) {
            return named;
        }
        if (status instanceof ProductionStatus ps && (ps.isWorkingOnProduction() || ps.isExtractingProduct())) {
            return workingTexture;
        }
        return null;
    }
}
