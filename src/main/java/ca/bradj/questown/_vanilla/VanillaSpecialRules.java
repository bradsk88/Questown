package ca.bradj.questown._vanilla;

import ca.bradj.questown.integration.SpecialRulesRegistry;

public class VanillaSpecialRules {
    public static void register() {
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
    }
}
