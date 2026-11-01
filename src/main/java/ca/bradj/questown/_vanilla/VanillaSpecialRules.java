package ca.bradj.questown._vanilla;

import ca.bradj.questown.integration.SpecialRulesRegistry;
import ca.bradj.questown.jobs.special.GrowTreesWarpRule;

public class VanillaSpecialRules {
    public static void register() {

        // This is purely for visual effect. The fisher uses simple loot-based result generation.
        SpecialRulesRegistry.registerSpecialRule(
                Vanilla.ResourceLocation("deploy_and_retract_fishing_hook"),
                new DeployFishingHookRule()
        );
        SpecialRulesRegistry.registerSpecialRule(
                Vanilla.ResourceLocation("check_tree_plantable"),
                new CheckTreePlantable()
        );
        SpecialRulesRegistry.registerSpecialRule(
                Vanilla.ResourceLocation("chop_down_tree"),
                new ChopDownTree()
        );
        // Warp-interleaved hook: grows planted saplings into real trees during time warp.
        SpecialRulesRegistry.registerSpecialRule(
                Vanilla.ResourceLocation("grow_trees_warp"),
                new GrowTreesWarpRule()
        );
    }
}
