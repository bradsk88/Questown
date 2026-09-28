package ca.bradj.questown.loader.neo;

import ca.bradj.questown.loader.ConfigSpec;
import net.neoforged.neoforge.common.ModConfigSpec;

/** NeoForge impl of the neutral {@link ConfigSpec}; wraps the built {@code ModConfigSpec}. */
public final class NeoConfigSpec implements ConfigSpec {
    private final ModConfigSpec spec;

    public NeoConfigSpec(ModConfigSpec spec) {
        this.spec = spec;
    }

    /** Expose the concrete spec for registration (loader-impl only). */
    public ModConfigSpec modSpec() {
        return spec;
    }
}
