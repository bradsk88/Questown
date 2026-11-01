package ca.bradj.questown.loader.forge;

import ca.bradj.questown.loader.ConfigSpec;
import net.minecraftforge.common.ForgeConfigSpec;

/** Forge 1.19.2 {@link ConfigSpec} (named apart from Forge's own {@code ForgeConfigSpec}). */
public final class ForgeConfigSpecHandle implements ConfigSpec {
    private final ForgeConfigSpec spec;

    public ForgeConfigSpecHandle(ForgeConfigSpec spec) {
        this.spec = spec;
    }

    public ForgeConfigSpec spec() {
        return spec;
    }
}
