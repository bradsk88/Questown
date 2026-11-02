package ca.bradj.questown.loader.neo;

import ca.bradj.questown.loader.ConfigSpec;
import net.neoforged.neoforge.common.ModConfigSpec;

public final class NeoConfigSpec implements ConfigSpec {
    private final ModConfigSpec spec;

    public NeoConfigSpec(ModConfigSpec spec) {
        this.spec = spec;
    }

    public ModConfigSpec modSpec() {
        return spec;
    }
}
