package ca.bradj.questown.loader.forge;

import ca.bradj.questown.Questown;
import ca.bradj.questown.core.init.EntitiesInit;
import ca.bradj.questown.mobs.helperchicken.HelperChickenEntity;
import net.minecraft.world.entity.animal.Chicken;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.event.entity.living.LivingChangeTargetEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;

public class HelperChickenEntityEvents {

    @EventBusSubscriber(modid = Questown.MODID, bus = EventBusSubscriber.Bus.MOD)
    public static class ModBus {
        @SubscribeEvent
        public static void entityAttrEvent(EntityAttributeCreationEvent event) {
            event.put(
                    EntitiesInit.HELPER_CHICKEN.get(),
                    Chicken.createAttributes().build()
            );
        }
    }

    /** Stops predators from targeting the helper chicken. */
    @EventBusSubscriber(modid = Questown.MODID, bus = EventBusSubscriber.Bus.FORGE)
    public static class ForgeBus {
        @SubscribeEvent
        public static void onTargetChange(LivingChangeTargetEvent event) {
            if (event.getNewTarget() instanceof HelperChickenEntity) {
                event.setCanceled(true);
            }
        }
    }
}
