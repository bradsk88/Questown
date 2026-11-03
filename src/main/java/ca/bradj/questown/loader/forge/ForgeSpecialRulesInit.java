package ca.bradj.questown.loader.forge;

import ca.bradj.questown.Questown;
import ca.bradj.questown.integration.QuestownSpecialRules;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;

@EventBusSubscriber(modid = Questown.MODID, bus = EventBusSubscriber.Bus.MOD)
public final class ForgeSpecialRulesInit {

    @SubscribeEvent()
    public static void onCommonSetup(FMLCommonSetupEvent event) {
        QuestownSpecialRules.registerRules();
    }
}
