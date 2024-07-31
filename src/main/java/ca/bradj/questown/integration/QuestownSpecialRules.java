package ca.bradj.questown.integration;

import ca.bradj.questown.Questown;
import ca.bradj.questown.jobs.special.HarvestCropSpecialRule;
import ca.bradj.questown.jobs.special.UseItemOnBlockSpecialRule;
import ca.bradj.questown.jobs.SpecialRules;
import ca.bradj.questown.jobs.special.TillWorkspotSpecialRule;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;

@Mod.EventBusSubscriber(modid = Questown.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class QuestownSpecialRules {

    @SubscribeEvent()
    public static void register(FMLCommonSetupEvent event) {
        // This is what other mods should do if they want to implement special rules
        SpecialRulesRegistry.registerSpecialRule(
                Questown.ResourceLocation(SpecialRules.HARVEST_CROP),
                new HarvestCropSpecialRule()
        );
        SpecialRulesRegistry.registerSpecialRule(
                Questown.ResourceLocation(SpecialRules.USE_ITEM_ON_BLOCK),
                new UseItemOnBlockSpecialRule()
        );
        SpecialRulesRegistry.registerSpecialRule(
                Questown.ResourceLocation(SpecialRules.TILL_WORKSPOT),
                new TillWorkspotSpecialRule()
        );
    }
}
