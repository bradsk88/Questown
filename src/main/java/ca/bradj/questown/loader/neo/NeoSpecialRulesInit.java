package ca.bradj.questown.loader.neo;

import ca.bradj.questown.Questown;
import ca.bradj.questown.integration.QuestownSpecialRules;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;

@EventBusSubscriber(modid = Questown.MODID, bus = EventBusSubscriber.Bus.MOD)
public final class NeoSpecialRulesInit {

    @SubscribeEvent()
    public static void onCommonSetup(FMLCommonSetupEvent event) {
        QuestownSpecialRules.registerRules();
    }
}
