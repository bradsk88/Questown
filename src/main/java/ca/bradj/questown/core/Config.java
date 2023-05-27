package ca.bradj.questown.core;

import net.minecraftforge.common.ForgeConfigSpec;

public class Config {

    public static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();
    public static final ForgeConfigSpec SPEC;

    public static final String VILLAGE_START_QUESTS = "Village start quests";

    public static final String FILENAME = "questown-server.toml";

    static {
        BUILDER.push("Questown.Config");
        BUILDER.pop();
        SPEC = BUILDER.build();
    }
}
