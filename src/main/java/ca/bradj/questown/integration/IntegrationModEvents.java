package ca.bradj.questown.integration;

import ca.bradj.questown.Questown;
import ca.bradj.questown.jobs.special.HarvestCropSpecialRule;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;

@Mod.EventBusSubscriber(modid = Questown.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class IntegrationModEvents {

    @SubscribeEvent()
    public static void register(FMLCommonSetupEvent event) {
        // This is what other mods should do if they want to implement special rules
        SpecialRules.registerSpecialRule(
                Questown.ResourceLocation("harvest_crop"),
                new HarvestCropSpecialRule()
        );
    }
}

