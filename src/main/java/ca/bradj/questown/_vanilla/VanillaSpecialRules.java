package ca.bradj.questown._vanilla;

import ca.bradj.questown.integration.SpecialRulesRegistry;

public class VanillaSpecialRules {
    public static void register() {
        SpecialRulesRegistry.registerSpecialRule(
                Vanilla.ResourceLocation("deploy_and_retract_fishing_hook"),
                new DeployFishingHookRule()
        );
    }
}
