package ca.bradj.questown.loader.neo;

import ca.bradj.questown.Questown;
import ca.bradj.questown.integration.QuestownSpecialRules;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;

/**
 * Common-setup binding for the mod's own special-rule set (G6, 2026-10-13): the
 * registration list stays neutral in {@code QuestownSpecialRules.registerRules},
 * the loader bus call moves here so {@code integration/} names no loader API.
 */
@EventBusSubscriber(modid = Questown.MODID, bus = EventBusSubscriber.Bus.MOD)
public final class NeoSpecialRulesInit {

    @SubscribeEvent()
    public static void onCommonSetup(FMLCommonSetupEvent event) {
        QuestownSpecialRules.registerRules();
    }
}
