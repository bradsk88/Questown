package ca.bradj.questown.integration;

import ca.bradj.questown.Questown;
import ca.bradj.questown.jobs.SpecialRules;
import ca.bradj.questown.jobs.special.*;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;

@Mod.EventBusSubscriber(modid = Questown.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class QuestownSpecialRules {

    @SubscribeEvent()
    public static void register(FMLCommonSetupEvent event) {
        // This is what other mods should do if they want to implement special rules

        // If this rule is enabled for the "extract product" step and the workspot
        // is a full grown crop, the crop will be harvested (using its loot table)
        // and replaced with an age=0 version of the same crop.
        SpecialRulesRegistry.registerSpecialRule(
                Questown.ResourceLocation("harvest_crop"),
                new HarvestCropSpecialRule()
        );

        // If this rule is enabled for work where one or more items are used,
        // the last inserted will be used in the block before "extract" step of
        // the work is finished. E.g. planting seeds, using bonemeal on crops.
        // If the item cannot be used on the block, it will just be consumed.
        // (Note: the item itself must implement "useOn". If, instead, the
        // BLOCK is responsible for the "use item" behaviour, then this rule
        // won't work. See "compost_at_workspot" for an example implementation
        // for blocks which control the "use item" behaviour)
        SpecialRulesRegistry.registerSpecialRule(
                Questown.ResourceLocation("use_last_inserted_item_on_block"),
                new UseLastInsertedItemOnBlockSpecialRule()
        );

        // If this rule is enabled for a processing state and the workspot is a
        // tillable block, then the block will be tilled before moving to the
        // next processing state.
        SpecialRulesRegistry.registerSpecialRule(
                Questown.ResourceLocation("till_workspot"),
                new TillWorkspotSpecialRule()
        );

        // If this rule is active for the "extract" state, and the workspot is
        // a Minecraft composter block, then the worker will attempt to extract
        // bone meal from the composter, which will also empty the composter.
        SpecialRulesRegistry.registerSpecialRule(
                Questown.ResourceLocation("compost_at_workspot"),
                new CompostAtWorkspotSpecialRule()
        );

        // See description in SpecialRules
        SpecialRulesRegistry.registerSpecialRule(
                Questown.ResourceLocation(SpecialRules.LIE_ON_WORKSPOT),
                new LieOnWorkspotSpecialRule()
        );
        // See description in SpecialRules
        SpecialRulesRegistry.registerSpecialRule(
                Questown.ResourceLocation(SpecialRules.CLEAR_POSE),
                new ClearPoseSpecialRule()
        );
        // See description in SpecialRules
        SpecialRulesRegistry.registerSpecialRule(
                Questown.ResourceLocation(SpecialRules.HUNGER_FILL_HALF),
                new FillHungerSpecialRule(0.5f)
        );
    }
}
