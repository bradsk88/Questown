package ca.bradj.questown.core;

import ca.bradj.questown.Questown;
import com.google.common.collect.ImmutableList;
import joptsimple.internal.Strings;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.common.ForgeConfigSpec;

import java.util.List;

public class Config {

    public static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();
    public static final ForgeConfigSpec SPEC;

    public static final String VILLAGE_START_QUESTS = "Village start quests";

    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> village_start_quests;

    public static final String FILENAME = "questown-common.toml";

    static {
        BUILDER.push("Questown.Config");

        village_start_quests = BUILDER.comment(
                "These are the quests that will be added to a newly activated town flag"
        ).defineList(
                VILLAGE_START_QUESTS,
                ImmutableList.of(
                        new ResourceLocation(Questown.MODID, "crafting_room").toString()
                ),
                (Object v) -> !Strings.isNullOrEmpty((String) v)
        );

        BUILDER.pop();
        SPEC = BUILDER.build();
    }
}
